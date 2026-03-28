"""
SO-Spindle Phase-Amplitude Coupling (PAC) — Delivery criterion.

Staresina et al. (2015) Nat Neurosci showed that memory consolidation
specifically benefits from cues delivered during SO-nested spindles —
spindles that occur during the SO up-state, not just any spindle.

The current v9 code checks SO phase and spindle probability independently.
This module implements cross-frequency PAC using the Modulation Index
(Tort et al. 2010) to identify optimal delivery windows where SO and
spindle rhythms are genuinely coupled.

Refs:
    Staresina et al. (2015) "Hierarchical nesting of slow oscillations,
        spindles and ripples in the human hippocampus during sleep"
    Tort et al. (2010) "Measuring phase-amplitude coupling between
        neuronal oscillations of different frequencies"
"""

from __future__ import annotations

from collections import deque
from typing import Deque

import numpy as np


class SOSpindleCoupling:
    """
    Real-time SO-spindle phase-amplitude coupling detector.

    Computes the Modulation Index (MI) of spindle amplitude as a function
    of SO phase on a rolling window. High MI indicates genuine SO-nested
    spindles — the optimal delivery window for TMR cues.

    Usage:
        coupling = SOSpindleCoupling(fs=250)
        for sample in eeg_stream:
            coupling.push(sample)
            mi = coupling.compute_mi()
            if mi > coupling.min_coupling:
                # High-value delivery window
                deliver_cue()
    """

    def __init__(
        self,
        fs: int = 250,
        so_lo: float = 0.5,
        so_hi: float = 1.0,
        spindle_lo: float = 11.0,
        spindle_hi: float = 16.0,
        window_s: float = 10.0,
        n_phase_bins: int = 18,
        min_coupling: float = 0.01,
    ) -> None:
        self.fs = fs
        self.so_lo = so_lo
        self.so_hi = so_hi
        self.spindle_lo = spindle_lo
        self.spindle_hi = spindle_hi
        self.n_phase_bins = n_phase_bins
        self.min_coupling = min_coupling
        self._buf: Deque[float] = deque(maxlen=int(fs * window_s))
        self._last_mi: float = 0.0

        # Pre-compute phase bins
        self._phase_bins = np.linspace(-np.pi, np.pi, n_phase_bins + 1)

        # Bandpass filters
        try:
            from scipy.signal import butter
            self._so_sos = butter(4, [so_lo, so_hi], btype="bandpass", fs=fs, output="sos")
            self._sp_sos = butter(4, [spindle_lo, spindle_hi], btype="bandpass", fs=fs, output="sos")
            self._scipy_ok = True
        except ImportError:
            self._scipy_ok = False

    def push(self, sample: float) -> None:
        self._buf.append(sample)

    def compute_mi(self) -> float:
        """
        Compute Modulation Index (Tort et al. 2010).

        MI = (log(N) - H) / log(N)
        where H = -Σ p(bin) * log(p(bin)) is the entropy of the
        amplitude distribution across phase bins, and N = number of bins.

        MI = 0: uniform distribution (no coupling)
        MI = 1: all amplitude in one phase bin (perfect coupling)

        Returns:
            Modulation index [0, 1]. Higher = stronger SO-spindle coupling.
        """
        if len(self._buf) < self.fs * 4 or not self._scipy_ok:
            return 0.0

        sig = np.array(self._buf, dtype=np.float64)

        from scipy.signal import sosfiltfilt, hilbert

        # SO phase
        so_filtered = sosfiltfilt(self._so_sos, sig)
        so_analytic = hilbert(so_filtered)
        so_phase = np.angle(so_analytic)

        # Spindle amplitude envelope
        sp_filtered = sosfiltfilt(self._sp_sos, sig)
        sp_analytic = hilbert(sp_filtered)
        sp_amplitude = np.abs(sp_analytic)

        # Bin spindle amplitude by SO phase
        bin_indices = np.digitize(so_phase, self._phase_bins) - 1
        bin_indices = np.clip(bin_indices, 0, self.n_phase_bins - 1)

        mean_amp = np.zeros(self.n_phase_bins)
        for b in range(self.n_phase_bins):
            mask = bin_indices == b
            if mask.any():
                mean_amp[b] = sp_amplitude[mask].mean()

        # Normalize to probability distribution
        total = mean_amp.sum()
        if total < 1e-12:
            self._last_mi = 0.0
            return 0.0

        p = mean_amp / total

        # Compute entropy
        p_nonzero = p[p > 0]
        h = -np.sum(p_nonzero * np.log(p_nonzero))

        # Modulation Index
        h_max = np.log(self.n_phase_bins)
        mi = (h_max - h) / h_max if h_max > 0 else 0.0

        self._last_mi = float(np.clip(mi, 0.0, 1.0))
        return self._last_mi

    def is_coupled(self) -> bool:
        """Check if current coupling exceeds threshold."""
        return self._last_mi >= self.min_coupling

    def get_preferred_phase(self) -> float | None:
        """
        Return the SO phase bin with highest spindle amplitude.
        This is the optimal phase for cue delivery.
        """
        if len(self._buf) < self.fs * 4 or not self._scipy_ok:
            return None

        sig = np.array(self._buf, dtype=np.float64)
        from scipy.signal import sosfiltfilt, hilbert

        so_filtered = sosfiltfilt(self._so_sos, sig)
        so_phase = np.angle(hilbert(so_filtered))
        sp_filtered = sosfiltfilt(self._sp_sos, sig)
        sp_amplitude = np.abs(hilbert(sp_filtered))

        bin_indices = np.digitize(so_phase, self._phase_bins) - 1
        bin_indices = np.clip(bin_indices, 0, self.n_phase_bins - 1)

        mean_amp = np.zeros(self.n_phase_bins)
        for b in range(self.n_phase_bins):
            mask = bin_indices == b
            if mask.any():
                mean_amp[b] = sp_amplitude[mask].mean()

        best_bin = int(np.argmax(mean_amp))
        center = (self._phase_bins[best_bin] + self._phase_bins[best_bin + 1]) / 2
        return float(center)

    @property
    def last_mi(self) -> float:
        return self._last_mi

    def get_diagnostics(self) -> dict:
        return {
            "last_mi": round(self._last_mi, 4),
            "min_coupling": self.min_coupling,
            "is_coupled": self.is_coupled(),
            "buffer_fill": len(self._buf),
            "buffer_capacity": self._buf.maxlen,
        }
