"""
Feature Extractor v13.3 — 15 features from 2-channel F3/F4
============================================================
All features computable from F3 + F4. No dummy data. Sample entropy fixed.
"""

import numpy as np
from scipy import signal as scipy_signal
from scipy.stats import entropy as scipy_entropy
from typing import Dict
import logging

from .signal_processor import BandPowerExtractor
from .config import SAMPLING_RATE, N_FEATURES, FEATURE_NAMES, FEATURE_INDEX, EEG_BANDS

logger = logging.getLogger("feature_extractor")


class FeatureExtractor:
    """15-D features from 2-channel F3/F4 EEG."""

    def __init__(self, fs: float = SAMPLING_RATE):
        self.fs = fs
        self.bp = BandPowerExtractor(fs=fs)

    def extract(self, eeg: np.ndarray) -> np.ndarray:
        """eeg: (2, n_samples) [F3, F4] in µV. Returns (15,)."""
        f3, f4 = eeg[0], eeg[1]
        frontal = (f3 + f4) / 2.0
        vec = np.zeros(N_FEATURES)

        # F01-F05: Band powers
        bands = self.bp.all_bands(frontal)
        for i, b in enumerate(["delta", "theta", "alpha", "beta", "gamma"]):
            vec[i] = bands[b]

        # F06: FAA ★ — ln(alpha_F4) - ln(alpha_F3)
        vec[5] = self.bp.band_power(f4, 8, 13) - self.bp.band_power(f3, 8, 13)

        # F07-F09: Ratios (log-space subtraction = log of ratio)
        vec[6] = self.bp.band_power(frontal, 4, 8) - self.bp.band_power(frontal, 13, 30)
        vec[7] = self.bp.band_power(frontal, 13, 30) - self.bp.band_power(frontal, 8, 13)
        vec[8] = self.bp.band_power(frontal, 30, 45) - self.bp.band_power(frontal, 4, 8)

        # F10: Alpha peak frequency
        freqs, psd = self.bp.psd(frontal)
        aidx = (freqs >= 7) & (freqs <= 14)
        vec[9] = freqs[aidx][np.argmax(psd[aidx])] if np.any(aidx) else 10.0

        # F11: Spectral entropy
        pn = psd / (np.sum(psd) + 1e-10)
        vec[10] = float(-np.sum(pn * np.log(pn + 1e-10)))

        # F12: F3-F4 coherence (alpha)
        vec[11] = self._coherence(f3, f4, 8, 13)

        # F13-F14: Hjorth
        vec[12], vec[13] = self._hjorth(frontal)

        # F15: Sample entropy
        vec[14] = self._sample_entropy(frontal)

        return np.nan_to_num(vec, nan=0.0, posinf=0.0, neginf=0.0)

    def extract_dict(self, eeg: np.ndarray) -> Dict[str, float]:
        vec = self.extract(eeg)
        return {name: float(vec[i]) for i, name in enumerate(FEATURE_NAMES)}

    def _coherence(self, left, right, fl, fh):
        try:
            f, C = scipy_signal.coherence(left, right, fs=self.fs, nperseg=min(256, len(left)))
            idx = (f >= fl) & (f <= fh)
            return float(np.mean(C[idx])) if np.any(idx) else 0.5
        except Exception as e:
            logger.warning(f"Coherence: {e}")
            return 0.5

    def _hjorth(self, x):
        if len(x) < 10: return 0.0, 0.0
        dx, ddx = np.diff(x), np.diff(x, n=2)
        vx, vdx, vddx = np.var(x)+1e-12, np.var(dx)+1e-12, np.var(ddx)+1e-12
        mob = np.sqrt(vdx / vx)
        return float(mob), float(np.sqrt(vddx / vdx) / max(mob, 1e-12))

    def _sample_entropy(self, x, m=2, r=0.2):
        """Richman & Moorman (2000). Self-match excluded with explicit mask."""
        try:
            n = min(len(x), 384)
            if n < 4 * m: return 0.0
            xs = x[:n]
            r_tol = r * (np.std(xs) + 1e-10)
            def _count(ml):
                N = n - ml
                if N < 2: return 0
                templates = np.array([xs[i:i+ml] for i in range(N)])
                count = 0
                for i in range(N):
                    diffs = np.max(np.abs(templates - templates[i]), axis=1)
                    mask = np.ones(N, dtype=bool)
                    mask[i] = False
                    count += int(np.sum(diffs[mask] <= r_tol))
                return count
            B, A = _count(m), _count(m + 1)
            return float(-np.log(A / B)) if B > 0 and A > 0 else 0.0
        except Exception as e:
            logger.warning(f"SampEn: {e}")
            return 0.0
