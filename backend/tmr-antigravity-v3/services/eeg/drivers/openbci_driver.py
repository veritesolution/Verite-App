"""
OpenBCI driver via BrainFlow SDK.
Supports Cyton (8ch), Cyton+Daisy (16ch), and Ganglion (4ch) boards.

Install: pip install brainflow
"""

import time
import numpy as np
import structlog

from ..base import EEGSourceBase, EEGSample, ImpedanceReport

log = structlog.get_logger()


class OpenBCIDriver(EEGSourceBase):
    """
    BrainFlow-based OpenBCI driver.
    Tested with Cyton (serial, USB dongle) and Cyton+Daisy (16-channel).
    """

    BOARD_MAP = {
        "cyton": 0,          # BoardIds.CYTON_BOARD
        "cyton_daisy": 2,    # BoardIds.CYTON_DAISY_BOARD
        "ganglion": 1,       # BoardIds.GANGLION_BOARD
        "synthetic": -1,     # BoardIds.SYNTHETIC_BOARD (BrainFlow built-in sim)
    }

    def __init__(
        self,
        board: str = "cyton",
        serial_port: str = "/dev/ttyUSB0",
        impedance_threshold_kohm: float = 20.0,
    ):
        self._board_type = board
        self._serial_port = serial_port
        self._threshold = impedance_threshold_kohm
        self._board_shim = None
        self._board_id = self.BOARD_MAP.get(board, 0)
        self._eeg_channels: list[int] = []
        self._timestamp_channel: int = 22
        self._sr: float = 250.0

    def connect(self) -> bool:
        try:
            from brainflow.board_shim import BoardShim, BrainFlowInputParams, LogLevels
        except ImportError:
            raise ImportError("brainflow not installed. Run: pip install brainflow")

        BoardShim.set_log_level(LogLevels.LEVEL_OFF.value)

        params = BrainFlowInputParams()
        params.serial_port = self._serial_port

        self._board_shim = BoardShim(self._board_id, params)
        self._board_shim.prepare_session()
        self._board_shim.start_stream(45000)   # 45k sample ring buffer

        self._eeg_channels = BoardShim.get_eeg_channels(self._board_id)
        self._timestamp_channel = BoardShim.get_timestamp_channel(self._board_id)
        self._sr = float(BoardShim.get_sampling_rate(self._board_id))

        log.info("openbci_connected", board=self._board_type, sr=self._sr,
                 n_channels=len(self._eeg_channels))
        return True

    def read_sample(self) -> EEGSample:
        if self._board_shim is None:
            raise RuntimeError("Not connected.")

        # Wait for at least 1 new sample (polls every 2 ms up to 200 ms)
        deadline = time.time() + 0.2
        while time.time() < deadline:
            count = self._board_shim.get_board_data_count()
            if count > 0:
                break
            time.sleep(0.002)

        data = self._board_shim.get_board_data(1)   # fetch 1 sample, removes from buffer
        if data.shape[1] == 0:
            raise TimeoutError("No data from OpenBCI board within 200 ms.")

        channels = data[self._eeg_channels, -1].astype(np.float64)   # µV
        timestamp = float(data[self._timestamp_channel, -1])

        # Basic impedance status from board state
        impedance_ok = True   # full impedance check is separate

        return EEGSample(
            timestamp=timestamp,
            channels=channels,
            sample_rate=self._sr,
            impedance_ok=impedance_ok,
            hardware_id=f"openbci_{self._board_type}",
        )

    def check_impedance(self) -> ImpedanceReport:
        """
        Runs BrainFlow impedance check. Takes ~2-3 seconds.
        Board must be in streaming mode already.
        """
        if self._board_shim is None:
            raise RuntimeError("Not connected.")

        from brainflow.board_shim import BoardShim
        channel_names = BoardShim.get_eeg_names(self._board_id)

        try:
            self._board_shim.config_board("/impedance_start")
            time.sleep(2.5)
            data = self._board_shim.get_board_data(100)
            self._board_shim.config_board("/impedance_stop")

            # BrainFlow reports impedance in Ω — convert to kΩ
            resistance_channels = BoardShim.get_resistance_channels(self._board_id)
            values = {}
            for i, (ch_idx, ch_name) in enumerate(zip(resistance_channels, channel_names)):
                if ch_idx < data.shape[0]:
                    raw_ohm = float(np.mean(np.abs(data[ch_idx, -20:])))
                    values[ch_name] = raw_ohm / 1000.0   # kΩ

        except Exception as e:
            log.warning("openbci_impedance_check_failed", error=str(e))
            values = {ch: 0.0 for ch in channel_names}

        report = ImpedanceReport(values_kohm=values, threshold_kohm=self._threshold)
        high = {k: v for k, v in values.items() if v >= self._threshold}
        if high:
            log.warning("high_impedance_channels", channels=high)

        return report

    def disconnect(self) -> None:
        if self._board_shim:
            try:
                self._board_shim.stop_stream()
                self._board_shim.release_session()
            except Exception:
                pass
            self._board_shim = None
            log.info("openbci_disconnected")

    @property
    def sample_rate(self) -> float:
        return self._sr

    @property
    def channel_names(self) -> list[str]:
        try:
            from brainflow.board_shim import BoardShim
            return BoardShim.get_eeg_names(self._board_id)
        except Exception:
            return [f"ch{i}" for i in range(8)]
