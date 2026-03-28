"""
Artefact Detector — Rule-based + ICA for production-grade artefact rejection.

Changes from v9:
    - MNE-Python ICA integration (was referenced but not connected)
    - Offline ICA fitting on baseline night, online apply during experiment
    - Clear separation of rule-based (real-time) vs ICA (offline post-processing)

Rule-based detection (real-time):
    - Peak-to-peak > threshold (motion/electrode pop)
    - Flatline detection (electrode detachment)
    - High-frequency power ratio (muscle EMG)

ICA-based rejection (offline, publication-grade):
    - Fit ICA on first baseline night
    - Identify and remove ocular + muscular components
    - Apply during experimental night for clean data
"""

from __future__ import annotations

import warnings
from collections import deque
from typing import Deque

import numpy as np


class ArtefactDetector:
    """
    Multi-stage artefact detection for EEG.

    Stage 1 (real-time): Rule-based amplitude/flatline/HF checks
    Stage 2 (offline): ICA via MNE-Python for publication-grade rejection
    """

    def __init__(
        self,
        fs: int = 250,
        n_channels: int = 1,
        amplitude_threshold_uv: float = 200.0,
        flatline_threshold: float = 0.5,
        hf_power_threshold: float = 50.0,
        window_s: float = 1.0,
        reference_channel: int = 0,
    ) -> None:
        self.fs = fs
        self.n_channels = n_channels
        self.reference_channel = reference_channel
        self.amplitude_threshold_uv = amplitude_threshold_uv
        self.flatline_threshold = flatline_threshold
        self.hf_power_threshold = hf_power_threshold
        # Multi-channel buffers
        self._bufs: list[Deque[float]] = [
            deque(maxlen=int(fs * window_s)) for _ in range(n_channels)
        ]
        self._buf = self._bufs[0]  # backwards compatibility
        self._artefact_count = 0
        self._total_windows = 0

        # ICA state
        self._ica = None
        self._ica_fitted = False

    def push(self, sample: float) -> None:
        self._buf.append(sample)

    def is_artefact(self) -> bool:
        """Real-time rule-based artefact check."""
        if len(self._buf) < self.fs // 2:
            return False

        sig = np.array(self._buf)
        self._total_windows += 1

        # Amplitude check
        if (sig.max() - sig.min()) > self.amplitude_threshold_uv:
            self._artefact_count += 1
            return True

        # Flatline check
        if sig.std() < self.flatline_threshold:
            self._artefact_count += 1
            return True

        # High-frequency power ratio
        try:
            from scipy.signal import butter, sosfiltfilt
            sos_hf = butter(4, 30.0, btype="high", fs=self.fs, output="sos")
            sos_bb = butter(4, [1.0, 45.0], btype="bandpass", fs=self.fs, output="sos")
            hf = np.mean(sosfiltfilt(sos_hf, sig) ** 2)
            bb = np.mean(sosfiltfilt(sos_bb, sig) ** 2) + 1e-9
            if hf / bb > self.hf_power_threshold:
                self._artefact_count += 1
                return True
        except ImportError:
            pass

        return False

    def fit_ica(
        self,
        baseline_eeg: np.ndarray,
        n_components: int = 15,
        channel_names: list[str] | None = None,
    ) -> dict:
        """
        Fit ICA on baseline night EEG data using MNE-Python.

        Call with data from the first baseline/calibration night.
        The fitted ICA can then be applied to subsequent nights.

        Args:
            baseline_eeg: EEG data array (channels × samples)
            n_components: Number of ICA components
            channel_names: Channel names (e.g., ["C3", "C4", "F3", "F4"])

        Returns:
            ICA fitting results
        """
        try:
            import mne
        except ImportError:
            return {"error": "MNE-Python required: pip install mne"}

        if baseline_eeg.ndim == 1:
            baseline_eeg = baseline_eeg.reshape(1, -1)

        n_channels = baseline_eeg.shape[0]
        if channel_names is None:
            channel_names = [f"EEG{i}" for i in range(n_channels)]

        info = mne.create_info(channel_names, self.fs, ch_types="eeg")
        raw = mne.io.RawArray(baseline_eeg * 1e-6, info, verbose=False)  # µV to V
        raw.filter(1.0, 40.0, verbose=False)

        ica = mne.preprocessing.ICA(
            n_components=min(n_components, n_channels),
            random_state=42,
            max_iter=800,
        )
        ica.fit(raw, verbose=False)

        # Auto-detect ocular and muscular components
        # EOG detection (if frontal channels available)
        excluded = []
        for ch in ["F3", "F4", "Fp1", "Fp2"]:
            if ch in channel_names:
                try:
                    eog_idx, eog_scores = ica.find_bads_eog(raw, ch_name=ch, verbose=False)
                    excluded.extend(eog_idx)
                except Exception:
                    pass

        # Muscle detection
        try:
            muscle_idx, muscle_scores = ica.find_bads_muscle(raw, verbose=False)
            excluded.extend(muscle_idx)
        except Exception:
            pass

        ica.exclude = list(set(excluded))
        self._ica = ica
        self._ica_fitted = True

        return {
            "n_components": ica.n_components_,
            "n_excluded": len(ica.exclude),
            "excluded_components": ica.exclude,
            "fitted": True,
        }

    def apply_ica(self, eeg_data: np.ndarray) -> np.ndarray:
        """Apply fitted ICA to remove artefact components."""
        if not self._ica_fitted or self._ica is None:
            warnings.warn("ICA not fitted. Call fit_ica() first.")
            return eeg_data

        try:
            import mne
            if eeg_data.ndim == 1:
                eeg_data = eeg_data.reshape(1, -1)
            info = mne.create_info(
                [f"EEG{i}" for i in range(eeg_data.shape[0])],
                self.fs, ch_types="eeg",
            )
            raw = mne.io.RawArray(eeg_data * 1e-6, info, verbose=False)
            clean = self._ica.apply(raw.copy(), verbose=False)
            return clean.get_data() * 1e6  # back to µV
        except Exception as e:
            warnings.warn(f"ICA apply failed: {e}")
            return eeg_data

    def push_multichannel(self, samples: list[float]) -> None:
        """Push one sample per channel simultaneously."""
        if len(samples) != self.n_channels:
            raise ValueError(f"Expected {self.n_channels} channels, got {len(samples)}")
        for ch_idx, sample in enumerate(samples):
            self._bufs[ch_idx].append(sample)

    def is_artefact_multichannel(self) -> bool:
        """Run artefact detection across all channels. True if ANY channel has artefact."""
        return any(self._check_channel(i) for i in range(self.n_channels))

    def _check_channel(self, ch_idx: int) -> bool:
        buf = self._bufs[ch_idx]
        if len(buf) < self.fs // 2: return False
        sig = np.array(buf)
        if (sig.max() - sig.min()) > self.amplitude_threshold_uv: return True
        if sig.std() < self.flatline_threshold: return True
        return False

    @property
    def artefact_rate_pct(self) -> float:
        if self._total_windows == 0:
            return 0.0
        return round(self._artefact_count / self._total_windows * 100, 1)

    @property
    def is_ica_fitted(self) -> bool:
        return self._ica_fitted
