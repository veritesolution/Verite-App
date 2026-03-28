"""
LMS Phase Estimator — Demo/development only.

⚠ NOT a validated EEG phase estimator. LMS tracks a fixed-frequency
sinusoidal reference, not real EEG slow oscillation dynamics.
Use ECHT or hardware for any experiment.
"""

from __future__ import annotations

from collections import deque

import numpy as np

from verite_tmr.phase.base import PhaseEstimator


class LMSPhaseEstimator(PhaseEstimator):
    """LMS adaptive filter phase tracker. DEMO ONLY."""

    name = "lms"
    expected_latency_ms = 4.0
    suitable_for_experiments = False

    def __init__(
        self, fs: int = 250, so_lo: float = 0.5, so_hi: float = 1.0,
        mu: float = 0.005, n_taps: int = 32,
    ) -> None:
        super().__init__(fs=fs, so_lo=so_lo, so_hi=so_hi)
        self.mu = mu
        self.n_taps = n_taps
        self._w = np.zeros(n_taps)
        self._buf: deque = deque(maxlen=int(fs * 2.0))

    def push_and_estimate(self, sample: float) -> float:
        self._buf.append(sample)
        if len(self._buf) < self.n_taps + 5:
            return self._phase_last

        sig = np.array(self._buf)
        t = np.arange(len(sig)) / self.fs
        ref = np.sin(2 * np.pi * 0.75 * t)
        n = min(self.n_taps, len(sig))
        xv = sig[-n:][::-1]
        y = np.dot(self._w[:n], xv)
        e = ref[-1] - y
        self._w[:n] += self.mu * e * xv
        phase = float(np.arctan2(e, y) % (2 * np.pi))
        self._phase_last = phase
        return phase

    def reset(self) -> None:
        super().reset()
        self._w[:] = 0.0
        self._buf.clear()
