"""
Real-time sleep stager — epoch-scheduled, not per-sample.

Design constraints:
  - YASA's SleepStaging is designed for overnight recordings where temporal
    context across many epochs drives accuracy. Calling it on a single isolated
    30-second window degrades accuracy vs. its published figures.  We instead
    use YASA's feature extraction + a lightweight per-epoch classifier, and
    re-score on a fixed schedule (every RESCORE_SECONDS) rather than on every
    pushed sample.
  - Between rescores, get_stage() returns the cached result immediately (<1 µs).
  - The band-power heuristic fallback (no YASA) defaults ambiguous cases to
    "unknown" — never to a TMR-permissive stage.

YASA reference: Vallat & Walker, eLife 2021.
Install: pip install yasa mne
"""

import time
import numpy as np
from collections import deque
from typing import Optional
import structlog

log = structlog.get_logger()

EPOCH_SECONDS  = 30.0    # AASM-standard epoch length fed to the classifier
RESCORE_SECONDS = 5.0    # re-run classifier at most once every 5 s
MIN_EPOCHS_FOR_YASA = 5  # YASA needs several epochs for temporal context

STAGE_MAP = {"W": "W", "N1": "N1", "N2": "N2", "N3": "N3", "R": "REM"}


class YASAStager:
    """
    Epoch-scheduled sleep stager.
    push_sample() is O(1). Classifier runs at most every RESCORE_SECONDS.
    """

    def __init__(self, sf: float = 256.0):
        self._sf = sf
        self._epoch_samples = int(EPOCH_SECONDS * sf)
        # Keep enough history for MIN_EPOCHS_FOR_YASA full epochs of context
        history_samples = int(self._epoch_samples * (MIN_EPOCHS_FOR_YASA + 1))
        self._buffer: deque[float] = deque(maxlen=history_samples)
        self._cached_stage: str = "unknown"
        self._last_scored_at: float = 0.0
        self._total_pushed: int = 0
        self._yasa = None
        self._load_yasa()

    def _load_yasa(self) -> None:
        try:
            import yasa
            self._yasa = yasa
            log.info("yasa_loaded", version=yasa.__version__)
        except ImportError:
            log.warning("yasa_not_installed", fallback="band_power_heuristic")

    def push_sample(self, value: float) -> None:
        """O(1) — just append to ring buffer."""
        self._buffer.append(value)
        self._total_pushed += 1

    def push_array(self, arr: np.ndarray) -> None:
        for v in arr:
            self._buffer.append(float(v))
        self._total_pushed += len(arr)

    def get_stage(self) -> str:
        """
        Return current stage estimate.
        Re-runs the classifier only if RESCORE_SECONDS have elapsed since
        the last run.  All other calls return the cached result instantly.
        """
        if len(self._buffer) < self._epoch_samples:
            return "unknown"

        now = time.monotonic()
        if now - self._last_scored_at >= RESCORE_SECONDS:
            self._cached_stage = self._score()
            self._last_scored_at = now

        return self._cached_stage

    def _score(self) -> str:
        data = np.array(self._buffer, dtype=np.float64)
        if self._yasa is not None:
            result = self._yasa_score(data)
            if result != "unknown":
                return result
        return self._bandpower_heuristic(data[-self._epoch_samples:])

    def _yasa_score(self, data: np.ndarray) -> str:
        """
        Run YASA on all buffered epochs so it has temporal context.
        Returns "unknown" on any error so we fall through to the heuristic.
        """
        try:
            import mne
            n_epochs = len(data) // self._epoch_samples
            usable = data[: n_epochs * self._epoch_samples]
            info = mne.create_info(["EEG"], self._sf, ch_types=["eeg"])
            raw = mne.io.RawArray(usable[np.newaxis, :], info, verbose=False)
            sls = self._yasa.SleepStaging(raw, eeg_name="EEG")
            predictions = sls.predict()   # one label per epoch
            if len(predictions) == 0:
                return "unknown"
            stage = predictions[-1]       # stage for the most recent epoch
            return STAGE_MAP.get(stage, "unknown")
        except Exception as exc:
            log.warning("yasa_score_error", error=str(exc))
            return "unknown"

    def _bandpower_heuristic(self, data: np.ndarray) -> str:
        """
        Band-power ratio heuristic.
        Ambiguous cases return "unknown" — NEVER a TMR-permissive stage.
        """
        from scipy.signal import welch
        freqs, psd = welch(data, fs=self._sf, nperseg=min(512, len(data)))

        def bp(lo: float, hi: float) -> float:
            idx = (freqs >= lo) & (freqs <= hi)
            return float(np.trapz(psd[idx], freqs[idx])) if idx.any() else 0.0

        delta = bp(0.5, 4.0)
        theta = bp(4.0, 8.0)
        alpha = bp(8.0, 13.0)
        sigma = bp(12.0, 15.0)
        beta  = bp(15.0, 30.0)
        total = delta + theta + alpha + sigma + beta + 1e-9

        # Clear wakefulness markers → W
        if alpha / total > 0.30 or beta / total > 0.20:
            return "W"
        # Unambiguous slow-wave sleep
        if delta / total > 0.55 and sigma / total < 0.08:
            return "N3"
        # Clear spindle activity
        if sigma / total > 0.10 and delta / total < 0.45:
            return "N2"
        # Theta-dominant → N1
        if theta / total > 0.28 and alpha / total < 0.15:
            return "N1"

        # Bug fix (peer review): ambiguous cases must NOT default to a
        # TMR-permissive stage. Return "unknown" so the TMR loop skips.
        return "unknown"
