"""
Sleep spindle detection, SO phase estimation, arousal risk estimation,
and artifact rejection.

v3.0.0 production fixes:
  ✅ Bug 2  — AR coefficients cached behind AR_REFIT_SECONDS (from v2.0.1)
  ✅ Bug 4  — Phase convention: 0 = positive peak, ±π = trough (from v2.0.1)
  🆕 Fix    — _extend_with_ar() now acquires _ar_lock on read side
              (eliminates logical data race on self._ar_coeffs)
  🆕 Feature — ArousalRiskEstimator: computes real-time arousal probability
              from beta/alpha ratio + EMG proxy + delta suppression.
              This was the phantom feature documented but never implemented.
"""

import time
import threading
import numpy as np
from collections import deque
from typing import Optional, Tuple
from scipy.signal import butter, sosfilt, hilbert
import structlog

log = structlog.get_logger()


# ── Artifact rejection ────────────────────────────────────────────────────────

class ArtifactRejector:
    """
    Rejects samples contaminated by blinks, muscle artifacts, or lead-off.
    Uses amplitude thresholding + peak-to-peak + variance z-score.
    """

    def __init__(
        self,
        amplitude_threshold_uv: float = 150.0,
        ptp_threshold_uv: float = 200.0,
        variance_z_threshold: float = 3.5,
        window_seconds: float = 1.0,
        sf: float = 256.0,
    ):
        self._amp_thr = amplitude_threshold_uv
        self._ptp_thr = ptp_threshold_uv
        self._z_thr   = variance_z_threshold
        self._window  = int(window_seconds * sf)
        self._buf: deque[float] = deque(maxlen=self._window)
        self._var_history: deque[float] = deque(maxlen=200)
        self._artifact_count: int = 0
        self._total_count: int = 0

    def push_and_check(self, sample_uv: float) -> bool:
        """Returns True if the sample is clean, False if artifact detected."""
        self._buf.append(sample_uv)
        self._total_count += 1

        if abs(sample_uv) > self._amp_thr:
            self._artifact_count += 1
            return False
        if len(self._buf) < self._window:
            return True

        arr = np.array(self._buf)
        if float(np.ptp(arr)) > self._ptp_thr:
            self._artifact_count += 1
            return False

        var = float(np.var(arr))
        self._var_history.append(var)
        if len(self._var_history) > 20:
            mu = float(np.mean(self._var_history))
            sd = float(np.std(self._var_history)) + 1e-9
            if (var - mu) / sd > self._z_thr:
                self._artifact_count += 1
                return False

        return True

    @property
    def artifact_rate(self) -> float:
        """Fraction of samples rejected as artifacts."""
        if self._total_count == 0:
            return 0.0
        return self._artifact_count / self._total_count


# ── Spindle detector ──────────────────────────────────────────────────────────

YASA_SPINDLE_RUN_SECONDS = 4.0
SPINDLE_DECAY_PER_SECOND = 0.4


class SpindleDetector:
    """
    Spindle probability estimator with a throttled YASA call path.
    """

    SIGMA_LOW  = 12.0
    SIGMA_HIGH = 15.0

    def __init__(self, sf: float = 256.0, window_seconds: float = 4.0):
        self._sf = sf
        self._window = int(window_seconds * sf)
        self._buf: deque[float] = deque(maxlen=self._window)
        self._baseline_rms: deque[float] = deque(maxlen=100)
        self._sos = butter(4, [self.SIGMA_LOW, self.SIGMA_HIGH],
                           btype="bandpass", fs=sf, output="sos")
        self._yasa = None
        try:
            import yasa
            self._yasa = yasa
        except ImportError:
            log.warning("yasa_not_available_spindle_rms_fallback_active")

        self._last_yasa_run: float = 0.0
        self._last_yasa_prob: float = 0.0

    def push_sample(self, eeg_uv: float) -> None:
        self._buf.append(eeg_uv)

    def get_spindle_probability(self) -> float:
        """Returns estimated spindle probability [0, 1]."""
        if len(self._buf) < self._window:
            return 0.0

        data = np.array(self._buf, dtype=np.float64)
        rms_prob = self._rms_probability(data)

        now = time.monotonic()
        if self._yasa is not None and (now - self._last_yasa_run) >= YASA_SPINDLE_RUN_SECONDS:
            self._last_yasa_prob = self._yasa_probability(data)
            self._last_yasa_run = now

        age = now - self._last_yasa_run
        decayed_yasa = max(0.0, self._last_yasa_prob - age * SPINDLE_DECAY_PER_SECOND)

        return float(np.clip(max(rms_prob, decayed_yasa), 0.0, 1.0))

    def _yasa_probability(self, data: np.ndarray) -> float:
        try:
            sp = self._yasa.detect_spindles(
                data, sf=self._sf,
                freq_sp=(self.SIGMA_LOW, self.SIGMA_HIGH),
                verbose=False,
            )
            summary = sp.summary()
            if summary.empty:
                return 0.0
            last_end = summary["End"].max()
            epoch_duration = len(data) / self._sf
            if epoch_duration - last_end < 0.5:
                return 0.90
            return float(np.clip(len(summary) / 3.0, 0.0, 0.70))
        except Exception:
            return 0.0

    def _rms_probability(self, data: np.ndarray) -> float:
        filtered = sosfilt(self._sos, data)
        rms = float(np.sqrt(np.mean(filtered ** 2)))
        self._baseline_rms.append(rms)
        if len(self._baseline_rms) < 10:
            return 0.0
        baseline = float(np.percentile(self._baseline_rms, 25))
        ratio = rms / (baseline + 1e-9)
        prob = 1.0 / (1.0 + np.exp(-2.5 * (ratio - 2.0)))
        return float(np.clip(prob, 0.0, 1.0))


# ── Phase estimator ───────────────────────────────────────────────────────────

AR_REFIT_SECONDS = 0.5
AR_ORDER         = 16
AR_HISTORY       = 512
N_PRED_SAMPLES   = 64


class PhaseEstimator:
    """
    Causal real-time slow-oscillation phase estimator.

    Phase convention (standard scipy Hilbert):
      phase = 0   → positive peak of the SO  = up-state peak
      phase = ±π  → negative trough           = down-state

    v3.0.0 fix: _extend_with_ar() now acquires _ar_lock when reading
    self._ar_coeffs, eliminating the logical data race. The lock is held
    only for the brief snapshot copy, not for the extrapolation loop.

    Reference: Zrenner et al. (2020) Brain Stimulation.
    """

    SO_LOW  = 0.5
    SO_HIGH = 4.0
    BUFFER_SECONDS = 6.0

    def __init__(self, sf: float = 256.0):
        self._sf = sf
        self._buf_size = int(self.BUFFER_SECONDS * sf)
        self._buf: deque[float] = deque(maxlen=self._buf_size)
        self._sos = butter(4, [self.SO_LOW, self.SO_HIGH],
                           btype="bandpass", fs=sf, output="sos")
        self._ar_coeffs: Optional[np.ndarray] = None
        self._last_ar_fit: float = 0.0
        self._ar_lock = threading.Lock()

    def push_sample(self, eeg_uv: float) -> None:
        self._buf.append(eeg_uv)

    def get_phase(self) -> Optional[float]:
        """
        Instantaneous phase in radians of the most recent sample.
        Returns None until the buffer is full.
        Convention: 0 = SO positive peak (up-state).
        """
        if len(self._buf) < self._buf_size:
            return None

        data     = np.array(self._buf, dtype=np.float64)
        filtered = sosfilt(self._sos, data)

        now = time.monotonic()
        with self._ar_lock:
            needs_refit = (
                self._ar_coeffs is None
                or (now - self._last_ar_fit) >= AR_REFIT_SECONDS
            )

        if needs_refit:
            self._refit_ar(filtered)

        extended = self._extend_with_ar(filtered)
        analytic = hilbert(extended)
        return float(np.angle(analytic[len(filtered) - 1]))

    def is_in_upstate(
        self,
        half_window_rad: float = np.pi / 4,
    ) -> Tuple[bool, Optional[float]]:
        """
        Returns (in_upstate, phase_rad).
        in_upstate is True when |phase_rad| < half_window_rad.
        """
        phase = self.get_phase()
        if phase is None:
            return False, None
        return abs(phase) < half_window_rad, phase

    def _refit_ar(self, filtered: np.ndarray) -> None:
        with self._ar_lock:
            try:
                x = filtered[-min(AR_HISTORY, len(filtered)):]
                n = len(x)
                if n <= AR_ORDER:
                    return
                X = np.stack([x[i: n - AR_ORDER + i] for i in range(AR_ORDER)], axis=1)
                y = x[AR_ORDER:]
                coeffs, _, _, _ = np.linalg.lstsq(X, y, rcond=None)
                self._ar_coeffs = coeffs
                self._last_ar_fit = time.monotonic()
            except Exception as exc:
                log.warning("ar_refit_failed", error=str(exc))

    def _extend_with_ar(self, filtered: np.ndarray) -> np.ndarray:
        """
        v3.0.0 fix: acquire _ar_lock when reading self._ar_coeffs.
        Snapshot-copy under lock, then extrapolate without holding the lock.
        """
        with self._ar_lock:
            coeffs = self._ar_coeffs.copy() if self._ar_coeffs is not None else None

        if coeffs is None:
            return np.concatenate([filtered, np.zeros(N_PRED_SAMPLES)])
        try:
            extended = list(filtered)
            order = len(coeffs)
            for _ in range(N_PRED_SAMPLES):
                extended.append(float(np.dot(coeffs, extended[-order:])))
            return np.array(extended)
        except Exception:
            return np.concatenate([filtered, np.zeros(N_PRED_SAMPLES)])


# ── Arousal risk estimator ────────────────────────────────────────────────────
#
# Implements the arousal risk computation that was documented in the README
# and model schema but never actually coded ("phantom feature").
#
# Method: Real-time arousal probability from three EEG-derived features:
#   1. Beta/alpha power ratio — elevated beta during NREM → cortical activation
#   2. EMG proxy — high-frequency power as chin-EMG surrogate
#   3. Delta suppression — sudden delta drop → lightening sleep
#
# References:
#   - Perslev et al., npj Digital Medicine, 2021
#   - Halász et al., Sleep Medicine Reviews, 2004
#   - AASM Manual for the Scoring of Sleep, 3rd edition

AROUSAL_RISK_MAX = 0.25
AROUSAL_WINDOW_SECONDS = 4.0
AROUSAL_UPDATE_INTERVAL = 1.0


class ArousalRiskEstimator:
    """
    Real-time arousal probability estimator.

    Returns a float in [0, 1] representing the probability that the sleeper
    is transitioning toward wakefulness. Values above AROUSAL_RISK_MAX (0.25)
    should suppress TMR cue delivery.
    """

    def __init__(self, sf: float = 256.0):
        self._sf = sf
        self._window = int(AROUSAL_WINDOW_SECONDS * sf)
        self._buf: deque[float] = deque(maxlen=self._window)
        self._delta_baseline: deque[float] = deque(maxlen=60)
        self._cached_risk: float = 0.0
        self._last_computed: float = 0.0

        nyq = sf / 2.0
        if 30.0 < nyq:
            self._sos_beta = butter(3, [15.0, min(30.0, nyq - 1)],
                                    btype="bandpass", fs=sf, output="sos")
        else:
            self._sos_beta = None

        self._sos_alpha = butter(3, [8.0, 13.0],
                                 btype="bandpass", fs=sf, output="sos")
        self._sos_delta = butter(3, [0.5, 4.0],
                                 btype="bandpass", fs=sf, output="sos")

        if 70.0 < nyq:
            self._sos_hgamma = butter(3, [30.0, min(70.0, nyq - 1)],
                                      btype="bandpass", fs=sf, output="sos")
        else:
            self._sos_hgamma = None

    def push_sample(self, eeg_uv: float) -> None:
        self._buf.append(eeg_uv)

    def get_arousal_risk(self) -> float:
        """
        Returns arousal probability [0, 1].
        Recomputes at most once per AROUSAL_UPDATE_INTERVAL seconds.
        """
        if len(self._buf) < self._window:
            return 0.0

        now = time.monotonic()
        if (now - self._last_computed) < AROUSAL_UPDATE_INTERVAL:
            return self._cached_risk

        data = np.array(self._buf, dtype=np.float64)
        self._cached_risk = self._compute(data)
        self._last_computed = now
        return self._cached_risk

    def _compute(self, data: np.ndarray) -> float:
        try:
            # Feature 1: Beta/Alpha ratio
            alpha_power = self._band_rms(data, self._sos_alpha)
            beta_power = self._band_rms(data, self._sos_beta) if self._sos_beta else 0.0
            beta_alpha = beta_power / (alpha_power + 1e-9)

            # Feature 2: EMG proxy (high-gamma RMS)
            emg_proxy = self._band_rms(data, self._sos_hgamma) if self._sos_hgamma else 0.0

            # Feature 3: Delta suppression
            delta_power = self._band_rms(data, self._sos_delta)
            self._delta_baseline.append(delta_power)
            if len(self._delta_baseline) >= 10:
                baseline_delta = float(np.percentile(list(self._delta_baseline), 75))
                delta_suppression = max(0.0, 1.0 - delta_power / (baseline_delta + 1e-9))
            else:
                delta_suppression = 0.0

            # Combine via logistic function
            z = (
                1.8 * (beta_alpha - 0.8)
                + 0.06 * (emg_proxy - 8.0)
                + 2.5 * (delta_suppression - 0.2)
                - 1.0  # bias: default to low risk
            )
            risk = 1.0 / (1.0 + np.exp(-z))
            return float(np.clip(risk, 0.0, 1.0))

        except Exception as exc:
            log.warning("arousal_risk_compute_error", error=str(exc))
            return 0.0

    @staticmethod
    def _band_rms(data: np.ndarray, sos) -> float:
        if sos is None:
            return 0.0
        filtered = sosfilt(sos, data)
        return float(np.sqrt(np.mean(filtered ** 2)))
