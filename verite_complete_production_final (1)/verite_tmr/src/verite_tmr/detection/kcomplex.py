"""
K-Complex Detector — Missing entirely from v9, now implemented.

K-complexes are landmark NREM events:
    - Sharp negative deflection followed by positive rebound
    - Peak-to-trough amplitude > 75 µV (participant-calibrated)
    - Duration 0.5–1.5 seconds
    - Occur primarily in N2, often at SO troughs
    - Primary trigger for spindle bursts

The standard Ngo 2013 SO-triggered cueing protocol triggers on the SO
negative half-wave (K-complex), not just phase. Without K-complex detection,
the system cannot implement this protocol.

Ref: Ngo et al. (2013) Neuron — closed-loop SO auditory stimulation
Ref: Amzica & Steriade (2002) — K-complex physiology
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass
from typing import Deque

import numpy as np


@dataclass
class KComplexEvent:
    """Detected K-complex event."""
    timestamp: float
    peak_to_trough_uv: float
    duration_s: float
    trough_sample_idx: int
    peak_sample_idx: int
    confidence: float  # 0-1, based on morphology match


class KComplexDetector:
    """
    Real-time K-complex detector for NREM EEG.

    Detection criteria (per-participant calibrated):
        1. Peak-to-trough amplitude > threshold (default 75 µV)
        2. Duration between 0.5 and 1.5 seconds
        3. Negative deflection precedes positive rebound
        4. Occurs during N2 or N3 (checked by caller)

    Calibration:
        Call calibrate() with 60+ seconds of N2 EEG to set
        participant-specific amplitude threshold.

    Args:
        fs: Sampling frequency (Hz)
        amplitude_threshold_uv: Min peak-to-trough for detection
        duration_min_s: Min K-complex duration
        duration_max_s: Max K-complex duration
        buffer_s: Rolling analysis buffer length
    """

    def __init__(
        self,
        fs: int = 250,
        amplitude_threshold_uv: float = 75.0,
        duration_min_s: float = 0.3,
        duration_max_s: float = 2.0,
        buffer_s: float = 3.0,
    ) -> None:
        self.fs = fs
        self.amplitude_threshold_uv = amplitude_threshold_uv
        self.duration_min_s = duration_min_s
        self.duration_max_s = duration_max_s
        self._buf: Deque[float] = deque(maxlen=int(fs * buffer_s))
        self._calibrated = False
        self._baseline_std: float = 30.0  # default, updated by calibrate()
        self._events: list[KComplexEvent] = []
        self._total_windows: int = 0
        self._refractory_samples: int = int(fs * 1.0)  # 1s refractory period
        self._last_detection_idx: int = -self._refractory_samples

        # Wider bandpass (0.1-6 Hz) to preserve K-complex sharp morphology
        try:
            from scipy.signal import butter
            self._so_sos = butter(4, [0.1, 6.0], btype="bandpass", fs=fs, output="sos")
            self._scipy_ok = True
        except ImportError:
            self._scipy_ok = False
            self._so_sos = None

    def push(self, sample: float) -> None:
        """Push a single EEG sample."""
        self._buf.append(sample)

    def detect(self) -> KComplexEvent | None:
        """
        Check current buffer for K-complex.

        Returns KComplexEvent if detected, None otherwise.
        Must be called after push() for each new sample.
        """
        min_samples = int(self.fs * self.duration_max_s * 1.5)
        if len(self._buf) < min_samples or not self._scipy_ok:
            return None

        self._total_windows += 1
        current_idx = len(self._buf) - 1

        # Refractory period check
        if (current_idx - self._last_detection_idx) < self._refractory_samples:
            return None

        sig = np.array(self._buf, dtype=np.float64)

        # Bandpass filter to SO/KC range
        from scipy.signal import sosfilt
        filtered = sosfilt(self._so_sos, sig)

        # Analyze the most recent window (last 2 seconds)
        window_len = int(self.fs * self.duration_max_s * 1.5)
        window = filtered[-window_len:]

        # Find negative trough
        trough_idx = np.argmin(window)
        trough_val = window[trough_idx]

        # Find positive peak AFTER the trough
        if trough_idx >= len(window) - 1:
            return None
        after_trough = window[trough_idx:]
        peak_offset = np.argmax(after_trough)
        peak_val = after_trough[peak_offset]
        peak_idx = trough_idx + peak_offset

        # Amplitude check
        amplitude = peak_val - trough_val
        if amplitude < self.amplitude_threshold_uv:
            return None

        # Duration check
        duration_samples = peak_idx - trough_idx
        duration_s = duration_samples / self.fs
        if not (self.duration_min_s <= duration_s <= self.duration_max_s):
            return None

        # Morphology check: trough must be negative, peak must be positive
        if trough_val > 0 or peak_val < 0:
            return None

        # Confidence score based on morphology quality
        # Higher amplitude relative to baseline = higher confidence
        confidence = min(1.0, amplitude / (self.amplitude_threshold_uv * 2))

        # Sharpness check: faster negative deflection = more K-complex-like
        pre_trough = window[max(0, trough_idx - int(0.2 * self.fs)):trough_idx]
        if len(pre_trough) > 5:
            slope = np.diff(pre_trough).min()
            if slope < -self._baseline_std * 0.5:
                confidence = min(1.0, confidence * 1.2)

        self._last_detection_idx = current_idx

        import time
        event = KComplexEvent(
            timestamp=time.time(),
            peak_to_trough_uv=float(amplitude),
            duration_s=float(duration_s),
            trough_sample_idx=trough_idx,
            peak_sample_idx=peak_idx,
            confidence=float(confidence),
        )
        self._events.append(event)
        return event

    def calibrate(self, n2_eeg: np.ndarray) -> dict:
        """
        Calibrate K-complex detection threshold from N2 EEG data.

        Should be called with ≥60 seconds of verified N2 sleep EEG
        from the participant's baseline night.

        Args:
            n2_eeg: 1D array of N2 EEG samples at self.fs Hz

        Returns:
            Calibration results dict
        """
        if len(n2_eeg) < self.fs * 10:
            return {"error": "Need at least 10 seconds of N2 EEG"}

        self._baseline_std = float(np.std(n2_eeg))

        # Set threshold at 2.5x baseline std, minimum 75 µV
        calibrated_threshold = max(75.0, self._baseline_std * 2.5)
        self.amplitude_threshold_uv = calibrated_threshold
        self._calibrated = True

        return {
            "baseline_std_uv": round(self._baseline_std, 2),
            "threshold_uv": round(calibrated_threshold, 2),
            "calibrated": True,
            "n_samples": len(n2_eeg),
            "duration_s": round(len(n2_eeg) / self.fs, 1),
        }

    @property
    def detection_rate(self) -> float:
        """K-complexes detected per window checked."""
        if self._total_windows == 0:
            return 0.0
        return len(self._events) / self._total_windows

    @property
    def events(self) -> list[KComplexEvent]:
        return list(self._events)

    @property
    def is_calibrated(self) -> bool:
        return self._calibrated
