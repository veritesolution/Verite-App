"""Brain-Emotion System — 2-channel F3/F4 emotion recognition."""
from .config import VERSION, SAMPLING_RATE, N_CHANNELS, N_FEATURES, FEATURE_NAMES, FEATURE_INDEX
__version__ = VERSION
from .signal_processor import EEGPreprocessor, BandPowerExtractor
from .feature_extractor import FeatureExtractor
from .heca_engine import HECAEngine, EmotionState, SoundRecommendation, PsychologistContext
from .emotion_taxonomy import EmotionTaxonomy, EmotionResult
