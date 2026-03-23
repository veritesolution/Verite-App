"""
Muse EEG driver (Muse 2, Muse S, Muse 2016).
Requires BlueMuse (Windows) or muselsl running as a separate process,
which exposes the Muse data as an LSL stream.

Install: pip install muselsl pylsl
Usage:   muselsl stream --name Muse  # in a separate terminal
"""

import time
import numpy as np
import structlog

from ..base import EEGSourceBase, EEGSample, ImpedanceReport

log = structlog.get_logger()

MUSE_CHANNELS = ["TP9", "AF7", "AF8", "TP10"]    # Muse standard montage
MUSE_AUX_CHANNEL = "Right AUX"
MUSE_SAMPLE_RATE = 256.0                           # Hz


class MuseDriver(EEGSourceBase):
    """
    LSL-based Muse driver.
    Reads from the LSL stream created by muselsl or BlueMuse.
    """

    def __init__(
        self,
        stream_name: str = "Muse",
        timeout_s: float = 5.0,
        include_aux: bool = False,
    ):
        self._stream_name = stream_name
        self._timeout_s = timeout_s
        self._include_aux = include_aux
        self._inlet = None
        self._sr = MUSE_SAMPLE_RATE

    def connect(self) -> bool:
        try:
            import pylsl
        except ImportError:
            raise ImportError("pylsl not installed. Run: pip install pylsl")

        log.info("muse_searching", stream=self._stream_name, timeout=self._timeout_s)
        streams = pylsl.resolve_byprop("name", self._stream_name, timeout=self._timeout_s)
        if not streams:
            raise ConnectionError(
                f"No Muse LSL stream named '{self._stream_name}' found.\n"
                "Make sure muselsl is streaming: muselsl stream --name Muse"
            )
        self._inlet = pylsl.StreamInlet(streams[0], max_chunklen=1)
        info = self._inlet.info()
        self._sr = info.nominal_srate()
        log.info("muse_connected", sample_rate=self._sr)
        return True

    def read_sample(self) -> EEGSample:
        if self._inlet is None:
            raise RuntimeError("Not connected. Call connect() first.")
        import pylsl
        sample, timestamp = self._inlet.pull_sample(timeout=0.2)
        if sample is None:
            raise TimeoutError(
                "No EEG sample received within 200 ms. Check Muse battery and Bluetooth."
            )
        channels = np.array(sample[:4], dtype=np.float64)   # µV, 4 main channels
        return EEGSample(
            timestamp=timestamp or time.time(),
            channels=channels,
            sample_rate=self._sr,
            impedance_ok=True,   # Muse does not expose real-time impedance
            hardware_id="muse",
        )

    def check_impedance(self) -> ImpedanceReport:
        """
        Muse does not expose electrode impedance via LSL.
        Returns a placeholder report — use the Muse app to verify contact quality.
        """
        log.warning("muse_impedance_not_supported")
        return ImpedanceReport(
            values_kohm={ch: 0.0 for ch in MUSE_CHANNELS},
            all_ok=True,   # assume ok; user should verify in Muse app
        )

    def disconnect(self) -> None:
        if self._inlet:
            self._inlet.close_stream()
            self._inlet = None
            log.info("muse_disconnected")

    @property
    def sample_rate(self) -> float:
        return self._sr

    @property
    def channel_names(self) -> list[str]:
        return list(MUSE_CHANNELS)
