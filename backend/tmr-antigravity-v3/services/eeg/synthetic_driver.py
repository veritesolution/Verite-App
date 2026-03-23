"""
Synthetic EEG driver — replays physiologically realistic signals for CI/dev.
Generates N2 sleep EEG with realistic slow oscillations and sleep spindles.
No hardware required.
"""

import time
import numpy as np
from ..base import EEGSourceBase, EEGSample, ImpedanceReport


class SyntheticDriver(EEGSourceBase):
    """
    Generates realistic synthetic EEG mimicking N2 sleep.
    - Slow oscillations: 0.5–1 Hz, 75 µV peak
    - Sleep spindles: 12–15 Hz bursts, 30 µV, random occurrence
    - Pink noise background: 1/f
    - Simulated real-time playback rate
    """

    SR = 256.0
    CHANNELS = ["Fp1", "Fp2", "C3", "C4", "O1", "O2", "F3", "F4"]

    def __init__(
        self,
        spindle_rate_per_min: float = 2.0,
        noise_level: float = 10.0,
        realtime: bool = True,           # if True, sleeps to simulate real hardware timing
        fixture_path: str = None,        # optionally replay a numpy fixture file
    ):
        self._spindle_rate = spindle_rate_per_min / 60.0  # per second
        self._noise = noise_level
        self._realtime = realtime
        self._sample_idx = 0
        self._last_sample_time = time.time()
        self._spindle_phase = 0.0
        self._in_spindle = False
        self._spindle_remaining = 0

        if fixture_path:
            self._fixture = np.load(fixture_path)   # shape (n_channels, n_samples)
        else:
            self._fixture = None

    def connect(self) -> bool:
        self._start_time = time.time()
        return True

    def read_sample(self) -> EEGSample:
        if self._realtime:
            # Throttle to realistic sample rate
            elapsed = time.time() - self._last_sample_time
            target_interval = 1.0 / self.SR
            if elapsed < target_interval:
                time.sleep(target_interval - elapsed)
        self._last_sample_time = time.time()

        t = self._sample_idx / self.SR
        self._sample_idx += 1

        if self._fixture is not None:
            idx = self._sample_idx % self._fixture.shape[1]
            channels = self._fixture[:, idx].astype(np.float64)
        else:
            channels = self._generate_sample(t)

        return EEGSample(
            timestamp=time.time(),
            channels=channels,
            sample_rate=self.SR,
            impedance_ok=True,
            hardware_id="synthetic",
        )

    def _generate_sample(self, t: float) -> np.ndarray:
        n_ch = len(self.CHANNELS)

        # Slow oscillation (0.75 Hz) — dominant in N2/N3
        so = 75.0 * np.sin(2 * np.pi * 0.75 * t)

        # Sleep spindle (12–14 Hz, sigma band)
        if not self._in_spindle:
            if np.random.random() < self._spindle_rate / self.SR:
                self._in_spindle = True
                self._spindle_remaining = int(np.random.uniform(1.0, 3.0) * self.SR / 10)
                self._spindle_freq = np.random.uniform(12.0, 15.0)
        if self._in_spindle:
            spindle = 30.0 * np.sin(2 * np.pi * self._spindle_freq * t)
            self._spindle_remaining -= 1
            if self._spindle_remaining <= 0:
                self._in_spindle = False
        else:
            spindle = 0.0

        # Pink noise (1/f) approximation via cumulative sum of white noise
        noise = self._noise * np.random.randn(n_ch) * 0.5

        # Combine on primary channels, add spatial mixing
        base = so + spindle
        channels = base * (0.8 + 0.4 * np.random.rand(n_ch)) + noise
        return channels.astype(np.float64)

    def check_impedance(self) -> ImpedanceReport:
        return ImpedanceReport(
            values_kohm={ch: 0.0 for ch in self.CHANNELS},
            all_ok=True,
        )

    def disconnect(self) -> None:
        pass

    @property
    def sample_rate(self) -> float:
        return self.SR

    @property
    def channel_names(self) -> list[str]:
        return list(self.CHANNELS)


def get_driver(hardware: str = "synthetic", **kwargs) -> EEGSourceBase:
    """Factory function — selects correct driver based on config."""
    if hardware == "muse":
        from .drivers.muse_driver import MuseDriver
        return MuseDriver(**kwargs)
    elif hardware == "openbci":
        from .drivers.openbci_driver import OpenBCIDriver
        return OpenBCIDriver(**kwargs)
    elif hardware == "synthetic":
        return SyntheticDriver(**kwargs)
    else:
        raise ValueError(f"Unknown hardware: {hardware}. Choose: synthetic | muse | openbci")
