"""
EEG hardware abstraction layer.
Supports Muse (via pylsl/muselsl), OpenBCI (via brainflow), and synthetic driver.
"""

import time
import uuid
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Optional, Dict
import numpy as np
import structlog

log = structlog.get_logger()


@dataclass
class EEGSample:
    """A single EEG sample from any hardware source."""
    timestamp: float                  # Unix epoch (seconds, float precision)
    channels: np.ndarray              # shape (n_channels,), units: µV
    sample_rate: float                # Hz
    impedance_ok: bool                # True if all channels below threshold
    n_channels: int = 0
    hardware_id: str = ""

    def __post_init__(self):
        self.n_channels = len(self.channels)


@dataclass
class ImpedanceReport:
    values_kohm: Dict[str, float]     # channel_name -> kΩ
    threshold_kohm: float = 20.0
    all_ok: bool = False

    def __post_init__(self):
        self.all_ok = all(v < self.threshold_kohm for v in self.values_kohm.values())


class EEGSourceBase(ABC):
    """Abstract base for all EEG hardware backends."""

    @abstractmethod
    def connect(self) -> bool:
        """Establish connection to the device. Returns True on success."""

    @abstractmethod
    def read_sample(self) -> EEGSample:
        """Read and return the next available sample. May block briefly."""

    @abstractmethod
    def check_impedance(self) -> ImpedanceReport:
        """Run impedance check. Blocking, ~1-2 seconds on real hardware."""

    @abstractmethod
    def disconnect(self) -> None:
        """Cleanly disconnect from the device."""

    @property
    @abstractmethod
    def sample_rate(self) -> float:
        """Nominal sample rate in Hz."""

    @property
    @abstractmethod
    def channel_names(self) -> list[str]:
        """Ordered list of channel names."""
