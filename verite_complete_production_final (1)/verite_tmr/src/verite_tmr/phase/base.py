"""
Base class for real-time phase estimation algorithms.

All phase estimators must:
    1. Accept single EEG samples via push()
    2. Return instantaneous SO phase via estimate() in radians [0, 2π]
    3. Report their latency characteristics
    4. Be independently testable against synthetic sinusoids
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

import numpy as np

if TYPE_CHECKING:
    from verite_tmr.config import Config


class PhaseEstimator(ABC):
    """Abstract base for causal real-time SO phase estimators."""

    # Subclasses must set these
    name: str = "base"
    expected_latency_ms: float = 0.0
    suitable_for_experiments: bool = False

    def __init__(self, fs: int = 250, so_lo: float = 0.5, so_hi: float = 1.0) -> None:
        self.fs = fs
        self.so_lo = so_lo
        self.so_hi = so_hi
        self._phase_last: float = 0.0

    @abstractmethod
    def push_and_estimate(self, sample: float) -> float:
        """
        Push a new EEG sample and return estimated SO phase in radians [0, 2π].

        This must be CAUSAL — only use past and current samples.
        """
        ...

    def reset(self) -> None:
        """Reset internal state for a new session."""
        self._phase_last = 0.0

    @property
    def last_phase(self) -> float:
        return self._phase_last

    def validate_against_synthetic(
        self,
        freq_hz: float = 0.75,
        duration_s: float = 10.0,
        tolerance_deg: float = 15.0,
    ) -> dict:
        """
        Validate phase estimation against a known synthetic sinusoid.

        Returns:
            dict with keys: mean_error_deg, max_error_deg, passed, n_samples
        """
        n_samples = int(duration_s * self.fs)
        t = np.arange(n_samples) / self.fs
        # Use cosine so that Hilbert-derived phase = 2πft directly
        # (sin would introduce a -π/2 offset which is a convention issue, not an error)
        signal = np.cos(2 * np.pi * freq_hz * t)
        true_phase = (2 * np.pi * freq_hz * t) % (2 * np.pi)

        self.reset()

        # Skip first 3 seconds for filter + AR settling
        skip = int(3.0 * self.fs)
        errors = []

        for i in range(n_samples):
            est = self.push_and_estimate(signal[i])
            if i >= skip:
                # Circular error
                diff = est - true_phase[i]
                err = np.arctan2(np.sin(diff), np.cos(diff))
                errors.append(abs(np.degrees(err)))

        errors_arr = np.array(errors)
        mean_err = float(np.mean(errors_arr))
        max_err = float(np.max(errors_arr))
        p95_err = float(np.percentile(errors_arr, 95))

        return {
            "mean_error_deg": round(mean_err, 2),
            "max_error_deg": round(max_err, 2),
            "p95_error_deg": round(p95_err, 2),
            "passed": p95_err < tolerance_deg,
            "n_samples": len(errors),
            "tolerance_deg": tolerance_deg,
            "estimator": self.name,
        }


def create_phase_estimator(predictor_type: str, fs: int = 250) -> PhaseEstimator:
    """Factory function to create phase estimators by name."""
    from verite_tmr.phase.echt import ECHTEstimator, CausalPhaseEstimator
    from verite_tmr.phase.ar import ARPhaseEstimator
    from verite_tmr.phase.lms import LMSPhaseEstimator

    estimators = {
        "echt": ECHTEstimator,
        "causal_interp": CausalPhaseEstimator,
        "ar": ARPhaseEstimator,
        "lms": LMSPhaseEstimator,
    }

    if predictor_type == "hardware":
        # Hardware provides phase directly — return a pass-through
        return _HardwarePassthrough(fs=fs)

    cls = estimators.get(predictor_type)
    if cls is None:
        valid = list(estimators.keys()) + ["hardware"]
        raise ValueError(
            f"Unknown phase predictor '{predictor_type}'. Valid options: {valid}. "
            f"NOTE: 'hilbert_buffer' was removed in v10 — it is non-causal."
        )
    return cls(fs=fs)


class _HardwarePassthrough(PhaseEstimator):
    """Pass-through for hardware-provided phase values."""

    name = "hardware"
    expected_latency_ms = 0.0
    suitable_for_experiments = True

    def push_and_estimate(self, sample: float) -> float:
        # In hardware mode, phase comes from the device, not computed here
        return self._phase_last

    def set_phase(self, phase: float) -> None:
        """Called when hardware provides a phase value."""
        self._phase_last = phase % (2 * np.pi)
