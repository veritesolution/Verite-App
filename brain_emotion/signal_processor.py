"""
Hello world
Signal Processor v13.3 — 2-channel F3/F4 (BioAmp EXG Pill + ESP32-S3)
=======================================================================
CRITICAL FIXES from code review:
1. NO re-referencing. With 2 channels, CAR/median forces F3'=(F3-F4)/2
   and F4'=(F4-F3)/2, making them mirror images → FAA always 0.
   Use physical reference electrode (linked earlobes) instead.
2. ADC conversion FIXED: now subtracts midpoint (2048) before scaling.

Pipeline: ADC→µV → bandpass 1-45Hz → notch 50/60Hz → artifact clip → quality
"""

import numpy as np
from scipy.signal import butter, filtfilt, iirnotch, welch
from typing import Tuple, Dict
import logging

from .config import (
    SAMPLING_RATE, N_CHANNELS, ADC_TO_UV, ADC_MIDPOINT,
    BANDPASS_LOW, BANDPASS_HIGH, BANDPASS_ORDER,
    NOTCH_FREQS, NOTCH_Q,
    ARTIFACT_AMPLITUDE_UV, ARTIFACT_GRADIENT_UV,
    EEG_BANDS, USE_REREFERENCING,
)

logger = logging.getLogger("signal_processor")


class SignalQualityScorer:
    """Quality ∈ [0,1]. Nolan et al. (2010) FASTER."""

    def score(self, data: np.ndarray) -> float:
        if data.size == 0:
            return 0.0
        amp_ok = np.abs(data) <= ARTIFACT_AMPLITUDE_UV
        grad = np.diff(data, axis=-1)
        grad_ok = np.abs(grad) <= ARTIFACT_GRADIENT_UV
        if data.ndim == 2:
            grad_ok = np.concatenate([grad_ok, np.ones((data.shape[0], 1), dtype=bool)], axis=1)
        else:
            grad_ok = np.concatenate([grad_ok, [True]])
        return float(np.mean(amp_ok & grad_ok))


class EEGPreprocessor:
    """
    2-channel preprocessor. NO re-referencing (would destroy FAA).
    Requires physical reference electrode on hardware.
    """

    def __init__(self, fs: float = SAMPLING_RATE):
        self.fs = fs
        self.quality_scorer = SignalQualityScorer()
        self._init_filters()

    def _init_filters(self):
        """Pre-compute filter coefficients once."""
        nyq = 0.5 * self.fs
        self._bp_b, self._bp_a = butter(
            BANDPASS_ORDER, [BANDPASS_LOW / nyq, BANDPASS_HIGH / nyq], btype="band"
        )
        self._notch_coeffs = []
        for freq in NOTCH_FREQS:
            if freq < nyq:  # only add notch if below Nyquist
                b, a = iirnotch(freq / nyq, NOTCH_Q)
                self._notch_coeffs.append((b, a))

    def adc_to_uv(self, raw_adc: np.ndarray) -> np.ndarray:
        """
        Convert ESP32-S3 12-bit ADC values to microvolts.
        FIXED: subtracts midpoint (2048) before scaling.
        raw_adc=2048 → 0 µV. raw_adc=2100 → ~38 µV.
        """
        return (raw_adc.astype(np.float64) - ADC_MIDPOINT) * ADC_TO_UV

    def preprocess(self, data: np.ndarray) -> Tuple[np.ndarray, float, Dict]:
        """
        Parameters
        ----------
        data : (2, n_samples) in µV — [F3, F4]

        Returns
        -------
        clean : (2, n_samples)
        quality : float ∈ [0, 1]
        metadata : dict
        """
        if data.shape[0] != N_CHANNELS:
            raise ValueError(f"Expected {N_CHANNELS} channels, got {data.shape[0]}")
        if data.shape[1] < 10:
            raise ValueError(f"Window too short: {data.shape[1]} samples")

        meta = {"artifacts_clipped": 0, "quality_raw": 0.0}
        meta["quality_raw"] = self.quality_scorer.score(data)

        # Check for NaN/Inf before processing
        if np.any(~np.isfinite(data)):
            logger.warning("NaN/Inf in input data — replacing with zeros")
            data = np.nan_to_num(data, nan=0.0, posinf=0.0, neginf=0.0)

        # Bandpass 1-45 Hz
        x = filtfilt(self._bp_b, self._bp_a, data, axis=-1)

        # Notch filters
        for b, a in self._notch_coeffs:
            x = filtfilt(b, a, x, axis=-1)

        # Artifact clipping
        n_clipped = int(np.sum(np.abs(x) > ARTIFACT_AMPLITUDE_UV))
        x = np.clip(x, -ARTIFACT_AMPLITUDE_UV, ARTIFACT_AMPLITUDE_UV)
        meta["artifacts_clipped"] = n_clipped

        # ════════════════════════════════════════════════════════════════
        # NO RE-REFERENCING.
        #
        # With only 2 channels, subtracting the mean across channels
        # (CAR) makes F3' = (F3-F4)/2 and F4' = (F4-F3)/2.
        # This forces correlation = -1 and FAA = 0 for ALL inputs.
        #
        # The system requires a physical reference electrode
        # (linked earlobes or mastoids) on the hardware.
        # ════════════════════════════════════════════════════════════════

        quality = self.quality_scorer.score(x)
        return x, quality, meta


class BandPowerExtractor:
    """Welch PSD band powers."""

    def __init__(self, fs: float = SAMPLING_RATE):
        self.fs = fs
        self.nperseg = min(256, int(fs * 2))

    def psd(self, x: np.ndarray):
        return welch(x, fs=self.fs, nperseg=min(self.nperseg, len(x)))

    def band_power(self, x: np.ndarray, low: float, high: float) -> float:
        freqs, psd_vals = self.psd(x)
        idx = (freqs >= low) & (freqs <= high)
        if not np.any(idx):
            return np.log(1e-10)
        try:
            integral = np.trapezoid(psd_vals[idx], freqs[idx])
        except AttributeError:
            integral = np.trapz(psd_vals[idx], freqs[idx])
        return float(np.log(max(integral, 1e-10)))

    def all_bands(self, x: np.ndarray) -> Dict[str, float]:
        return {b: self.band_power(x, lo, hi) for b, (lo, hi) in EEG_BANDS.items()}
