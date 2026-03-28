"""Sleep event detection: spindles, K-complexes, arousal, artefacts."""

from verite_tmr.detection.spindle_cnn import SpindleCNN
from verite_tmr.detection.kcomplex import KComplexDetector
from verite_tmr.detection.arousal import ArousalPredictor
from verite_tmr.detection.artefact import ArtefactDetector
from verite_tmr.detection.coupling import SOSpindleCoupling

__all__ = [
    "SpindleCNN",
    "KComplexDetector",
    "ArousalPredictor",
    "ArtefactDetector",
    "SOSpindleCoupling",
]
