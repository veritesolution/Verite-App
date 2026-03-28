"""
AR Phase Estimator — Burg AR(30) causal phase prediction.

Backup option when ECHT is unavailable. ~120ms latency.
Ref: Zrenner et al. (2020) — AR model component of ECHT pipeline.

⚠ WARNING: 120ms latency + audio playback may exceed the ~250ms SO up-state
window. Validate total end-to-end latency on actual hardware before use.
"""

from __future__ import annotations

from collections import deque
from typing import Deque

import numpy as np

from verite_tmr.phase.base import PhaseEstimator


class ARPhaseEstimator(PhaseEstimator):
    """
    Burg AR(30) causal phase predictor.

    Uses the same Burg AR fitting as ECHT but without the forward-prediction
    and endpoint correction. This makes it faster but less accurate.
    """

    name = "ar"
    expected_latency_ms = 120.0
    suitable_for_experiments = True  # conditional — validate latency first

    def __init__(
        self,
        fs: int = 250,
        so_lo: float = 0.5,
        so_hi: float = 1.0,
        ar_order: int = 30,
        buffer_s: float = 2.0,
    ) -> None:
        super().__init__(fs=fs, so_lo=so_lo, so_hi=so_hi)
        self.ar_order = ar_order
        self._buf: Deque[float] = deque(maxlen=int(fs * buffer_s))

        try:
            from scipy.signal import butter
            self._so_sos = butter(4, [so_lo, so_hi], btype="bandpass", fs=fs, output="sos")
            self._scipy_ok = True
        except ImportError:
            self._scipy_ok = False
            self._so_sos = None

    def push_and_estimate(self, sample: float) -> float:
        self._buf.append(sample)

        if len(self._buf) < self.ar_order + 10 or not self._scipy_ok:
            return self._phase_last

        from scipy.signal import sosfiltfilt, hilbert as sph

        sig = np.array(self._buf, dtype=np.float64)
        filtered = sosfiltfilt(self._so_sos, sig)
        analytic = sph(filtered)

        # Safe interior point to avoid Hilbert endpoint artifacts
        buf_len = len(filtered)
        safe_idx = int(buf_len * 0.6)
        safe_idx = max(10, min(safe_idx, buf_len - 10))

        phase_safe = np.angle(analytic[safe_idx])

        # Instantaneous frequency estimation
        window = min(5, safe_idx - 1)
        phases_nearby = np.unwrap(
            np.angle(analytic[safe_idx - window: safe_idx + window + 1])
        )
        if len(phases_nearby) >= 3:
            inst_freq = float(np.mean(np.diff(phases_nearby)))
        else:
            inst_freq = 2 * np.pi * 0.75 / self.fs

        remaining = buf_len - 1 - safe_idx
        phase = float((phase_safe + inst_freq * remaining) % (2 * np.pi))
        self._phase_last = phase
        return phase

    def reset(self) -> None:
        super().reset()
        self._buf.clear()

    @staticmethod
    def _burg_ar(x: np.ndarray, order: int) -> np.ndarray:
        """Burg method AR coefficient estimation."""
        n = len(x)
        if n <= order:
            return np.zeros(order)
        ef = x.astype(np.float64).copy()
        eb = x.astype(np.float64).copy()
        a = np.zeros(order, dtype=np.float64)
        for m in range(order):
            ef_s = ef[m + 1:]
            eb_s = eb[m: n - 1]
            num = -2.0 * np.dot(ef_s, eb_s)
            den = np.dot(ef_s, ef_s) + np.dot(eb_s, eb_s) + 1e-12
            km = np.clip(num / den, -0.999, 0.999)
            ef_new = ef_s + km * eb_s
            eb_new = eb_s + km * ef_s
            ef[m + 1:] = ef_new
            eb[m: n - 1] = eb_new
            a_new = np.zeros(m + 1, dtype=np.float64)
            a_new[m] = km
            for k in range(m):
                a_new[k] = a[k] + km * a[m - 1 - k]
            a[: m + 1] = a_new
        return a
