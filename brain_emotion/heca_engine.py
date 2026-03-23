"""
HECA Engine v13.3 — Hierarchical Emotion Certainty Architecture
================================================================
FIXES from code review:
1. Confidence = max(proba[0], proba[1]), NOT raw proba[1]
2. Per-user calibration via z-scoring against baseline
3. Arousal labeled SPECULATIVE (2ch frontal is weak for arousal)
4. Graceful degradation when signal quality is low
5. Sound therapy labeled EXPERIMENTAL
"""

import numpy as np
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple
from collections import deque
import time
import logging

from .emotion_taxonomy import EmotionTaxonomy, EmotionResult
from .signal_processor import EEGPreprocessor
from .feature_extractor import FeatureExtractor
from .config import (
    SAMPLING_RATE, N_FEATURES, FEATURE_NAMES, FEATURE_INDEX,
    BASIC_EMOTIONS, SOUND_PROFILES, BINAURAL_FREQUENCIES,
    SOUND_TRANSITION_SECONDS, MIN_STATE_DURATION, EMOTION_SMOOTHING_ALPHA,
    TIER1_CONFIDENCE_THRESHOLD, MIN_SIGNAL_QUALITY,
    EMOTION_TREND_WINDOW, CRISIS_VALENCE_THRESHOLD, CRISIS_DURATION_SECONDS,
    VALENCE_RELIABILITY, AROUSAL_RELIABILITY, ZSCORE_CLIP_RANGE,
)

logger = logging.getLogger("heca_engine")


# ═══ DATA STRUCTURES ═════════════════════════════════════════════════════

@dataclass
class EmotionState:
    timestamp: float
    valence: float
    arousal: float
    quadrant: str
    valence_confidence: float   # FIXED: max(p0,p1) — true model confidence
    arousal_confidence: float
    basic_emotion: str
    basic_emotion_prob: float
    basic_emotion_probs: Dict[str, float] = field(default_factory=dict)
    signal_quality: float = 0.0
    faa_value: float = 0.0
    smoothed_valence: float = 0.0
    smoothed_arousal: float = 0.0
    smoothed_quadrant: str = "HV_LA"
    valence_reliability: str = VALENCE_RELIABILITY
    arousal_reliability: str = AROUSAL_RELIABILITY
    is_calibrated: bool = False
    low_quality_warning: bool = False

    def to_dict(self) -> dict:
        return {
            "timestamp": self.timestamp,
            "valence": round(self.valence, 3),
            "arousal": round(self.arousal, 3),
            "quadrant": self.quadrant,
            "valence_confidence": round(self.valence_confidence, 3),
            "arousal_confidence": round(self.arousal_confidence, 3),
            "basic_emotion": self.basic_emotion,
            "basic_emotion_prob": round(self.basic_emotion_prob, 3),
            "basic_emotion_probs": {k: round(v, 3) for k, v in self.basic_emotion_probs.items()},
            "signal_quality": round(self.signal_quality, 3),
            "faa": round(self.faa_value, 3),
            "smoothed_valence": round(self.smoothed_valence, 3),
            "smoothed_arousal": round(self.smoothed_arousal, 3),
            "smoothed_quadrant": self.smoothed_quadrant,
            "valence_reliability": self.valence_reliability,
            "arousal_reliability": self.arousal_reliability,
            "is_calibrated": self.is_calibrated,
            "low_quality_warning": self.low_quality_warning,
        }


@dataclass
class SoundRecommendation:
    binaural_beat_hz: float
    binaural_carrier_hz: float
    nature_sounds: List[str]
    lofi_tempo_bpm: int
    lofi_key: str
    meditation_type: Optional[str]
    description: str
    should_transition: bool
    transition_seconds: float
    target_quadrant: str
    is_experimental: bool = True  # Binaural beats are NOT proven

    def to_dict(self) -> dict:
        return {
            "binaural": {"beat_hz": self.binaural_beat_hz, "carrier_hz": self.binaural_carrier_hz},
            "nature_sounds": self.nature_sounds,
            "lofi": {"tempo_bpm": self.lofi_tempo_bpm, "key": self.lofi_key},
            "meditation": self.meditation_type,
            "description": self.description,
            "should_transition": self.should_transition,
            "transition_seconds": self.transition_seconds,
            "target_quadrant": self.target_quadrant,
            "disclaimer": "EXPERIMENTAL: Binaural beat efficacy is not established. Nature sounds and breathing exercises have stronger evidence.",
        }


@dataclass
class PsychologistContext:
    current_emotion: Dict
    fine_grained_emotion: Optional[Dict]  # 26-class result with eeg_support level
    emotion_trend: str
    dominant_emotion_5min: str
    valence_trajectory: List[float]
    arousal_trajectory: List[float]
    crisis_flag: bool
    crisis_reason: Optional[str]
    session_duration_seconds: float
    recommendation: str

    def to_dict(self) -> dict:
        return {
            "current": self.current_emotion,
            "fine_grained": self.fine_grained_emotion,
            "trend": self.emotion_trend,
            "dominant_5min": self.dominant_emotion_5min,
            "valence_history": [round(v, 2) for v in self.valence_trajectory[-30:]],
            "arousal_history": [round(a, 2) for a in self.arousal_trajectory[-30:]],
            "crisis": {"flag": self.crisis_flag, "reason": self.crisis_reason},
            "session_seconds": round(self.session_duration_seconds, 1),
            "recommendation": self.recommendation,
            "reliability_note": f"Valence: {VALENCE_RELIABILITY}, Arousal: {AROUSAL_RELIABILITY}",
        }


# ═══ USER CALIBRATION ════════════════════════════════════════════════════

class UserCalibration:
    """
    Per-user z-score normalization against a 60-second baseline.
    Without this, population-level models perform poorly on individuals.
    """

    def __init__(self):
        self._baseline_features: List[np.ndarray] = []
        self._mean: Optional[np.ndarray] = None
        self._std: Optional[np.ndarray] = None
        self.is_calibrated = False

    def add_baseline_window(self, features: np.ndarray):
        """Add one window of features during calibration phase."""
        self._baseline_features.append(features.copy())

    def finalize(self):
        """Compute baseline mean and std after calibration recording."""
        if len(self._baseline_features) < 3:
            logger.warning(f"Calibration needs ≥3 windows, got {len(self._baseline_features)}")
            return False
        mat = np.array(self._baseline_features)
        self._mean = np.mean(mat, axis=0)
        self._std = np.std(mat, axis=0) + 1e-10  # avoid division by zero
        self.is_calibrated = True
        logger.info(f"Calibration done: {len(self._baseline_features)} windows")
        return True

    def normalize(self, features: np.ndarray) -> np.ndarray:
        """Z-score features against user's baseline."""
        if not self.is_calibrated:
            return features
        return (features - self._mean) / self._std


# ═══ MAIN ENGINE ═════════════════════════════════════════════════════════

class HECAEngine:
    """
    Process 2-channel EEG → (EmotionState, SoundRecommendation, PsychologistContext).
    """

    def __init__(self, fs: float = SAMPLING_RATE):
        self.fs = fs
        self.preprocessor = EEGPreprocessor(fs=fs)
        self.extractor = FeatureExtractor(fs=fs)
        self.calibration = UserCalibration()
        self.taxonomy = EmotionTaxonomy()

        self._val_clf = None
        self._aro_clf = None
        self._history: deque = deque(maxlen=1000)
        self._session_start = time.time()
        self._smooth_valence = 0.0
        self._smooth_arousal = 0.0
        self._current_quadrant = "HV_LA"
        self._quadrant_since = time.time()
        self._tier_status = "PENDING"

    def load_model(self, val_clf, aro_clf):
        self._val_clf = val_clf
        self._aro_clf = aro_clf

    def set_status(self, status: str):
        self._tier_status = status

    def process_window(self, eeg: np.ndarray, context: Optional[Dict] = None):
        """
        Process one EEG window.

        Parameters
        ----------
        eeg : (2, n_samples) — [F3, F4] in µV
        context : optional dict with keys like "mode", "content", "interaction"
                  Used to disambiguate fine-grained emotions (26 classes).

        Returns
        -------
        (emotion_state, fine_grained_emotion, sound_recommendation, psychologist_context)
        """
        ts = time.time()
        clean, quality, meta = self.preprocessor.preprocess(eeg)
        features = self.extractor.extract(clean)
        features_norm = self.calibration.normalize(features)
        low_quality = quality < MIN_SIGNAL_QUALITY
        if low_quality:
            logger.warning(f"Low signal quality: {quality:.1%}")

        emotion = self._compute_emotion(features, features_norm, quality, ts, low_quality)
        self._history.append(emotion)

        # Fine-grained emotion (26 classes) using V/A + context
        fine = self.taxonomy.classify(
            emotion.smoothed_valence, emotion.smoothed_arousal, context
        )

        sound = self._compute_sound(emotion)
        psych = self._compute_psychologist_context(emotion, fine)
        return emotion, fine, sound, psych

    def process_raw_adc(self, raw_adc: np.ndarray, context: Optional[Dict] = None):
        """Process raw ESP32 ADC values."""
        return self.process_window(self.preprocessor.adc_to_uv(raw_adc), context)

    def add_calibration_window(self, eeg: np.ndarray):
        """Add a baseline window during calibration phase."""
        clean, quality, _ = self.preprocessor.preprocess(eeg)
        if quality >= MIN_SIGNAL_QUALITY:
            features = self.extractor.extract(clean)
            self.calibration.add_baseline_window(features)
            return True
        return False

    def finalize_calibration(self) -> bool:
        return self.calibration.finalize()

    # ── Emotion ──────────────────────────────────────────────────────────

    def _compute_emotion(self, features_raw, features_norm, quality, ts, low_quality):
        # Use calibrated features when available, raw otherwise.
        # This ensures calibration works in BOTH classifier and heuristic mode.
        feat = features_norm if self.calibration.is_calibrated else features_raw
        faa = feat[FEATURE_INDEX["faa"]]

        # ── VALENCE (via FAA — MODERATE reliability) ─────────────────────
        if self._val_clf is not None and not low_quality:
            proba = self._val_clf.predict_proba(features_norm.reshape(1, -1))[0]
            val_conf = float(max(proba[0], proba[1]))
            valence = (float(proba[1]) - 0.5) * 2.0
        else:
            # FAA sign: POSITIVE FAA = more right alpha = more LEFT activation
            # = approach motivation = POSITIVE valence (Davidson 1992)
            #
            # SATURATION FIX (v13.3): Clip z-scored features to [-3, +3].
            # Without clipping, calibrated z-scores can be huge (e.g., +39.8)
            # causing tanh to saturate at ±1.0 and lose all emotional gradation.
            # ±3 SD preserves 99.7% of the normal distribution's sensitivity.
            faa_clipped = float(np.clip(faa, -ZSCORE_CLIP_RANGE, ZSCORE_CLIP_RANGE))
            valence = float(np.tanh(faa_clipped * 0.8))
            val_conf = min(abs(valence) * 0.8 + 0.5, 1.0)

        # ── AROUSAL (SPECULATIVE for 2ch frontal) ────────────────────────
        if self._aro_clf is not None and not low_quality:
            proba = self._aro_clf.predict_proba(features_norm.reshape(1, -1))[0]
            aro_conf = float(max(proba[0], proba[1]))
            arousal = (float(proba[1]) - 0.5) * 2.0
        else:
            ba = float(np.clip(feat[FEATURE_INDEX["beta_alpha_ratio"]], -ZSCORE_CLIP_RANGE, ZSCORE_CLIP_RANGE))
            arousal = float(np.tanh(ba * 0.5))
            aro_conf = min(abs(arousal) * 0.5 + 0.3, 0.8)

        # Graceful degradation: reduce confidence when quality is low
        if low_quality:
            val_conf *= quality / MIN_SIGNAL_QUALITY
            aro_conf *= quality / MIN_SIGNAL_QUALITY

        valence = float(np.clip(valence, -1, 1))
        arousal = float(np.clip(arousal, -1, 1))

        # Quadrant
        if valence >= 0 and arousal >= 0: q = "HV_HA"
        elif valence >= 0: q = "HV_LA"
        elif arousal >= 0: q = "LV_HA"
        else: q = "LV_LA"

        # Smoothing
        a = EMOTION_SMOOTHING_ALPHA
        self._smooth_valence = a * valence + (1 - a) * self._smooth_valence
        self._smooth_arousal = a * arousal + (1 - a) * self._smooth_arousal
        sv, sa = self._smooth_valence, self._smooth_arousal
        if sv >= 0 and sa >= 0: sq = "HV_HA"
        elif sv >= 0: sq = "HV_LA"
        elif sa >= 0: sq = "LV_HA"
        else: sq = "LV_LA"

        basic, bp, all_p = self._map_basic(valence, arousal)

        return EmotionState(
            timestamp=ts, valence=valence, arousal=arousal, quadrant=q,
            valence_confidence=val_conf, arousal_confidence=aro_conf,
            basic_emotion=basic, basic_emotion_prob=bp, basic_emotion_probs=all_p,
            signal_quality=quality, faa_value=faa,
            smoothed_valence=self._smooth_valence, smoothed_arousal=self._smooth_arousal,
            smoothed_quadrant=sq, is_calibrated=self.calibration.is_calibrated,
            low_quality_warning=low_quality,
        )

    def _map_basic(self, v, a):
        scores = {}
        for name, coords in BASIC_EMOTIONS.items():
            tv, ta = coords["valence"], coords["arousal"]
            d = np.sqrt((v - tv)**2 + (a - ta)**2)
            scores[name] = max(1.0 - d / np.sqrt(8.0), 0.0)
        vals = np.array(list(scores.values()))
        e = np.exp(vals - np.max(vals))
        probs = e / (np.sum(e) + 1e-10)
        names = list(scores.keys())
        pd = {n: float(p) for n, p in zip(names, probs)}
        best = names[np.argmax(probs)]
        return best, float(np.max(probs)), pd

    def _compute_sound(self, emotion):
        sq = emotion.smoothed_quadrant
        profile = SOUND_PROFILES[sq]
        binaural = BINAURAL_FREQUENCIES[profile["binaural"]]
        now = time.time()
        should = False
        if sq != self._current_quadrant and now - self._quadrant_since >= MIN_STATE_DURATION:
            should = True
            self._current_quadrant = sq
            self._quadrant_since = now
        return SoundRecommendation(
            binaural_beat_hz=binaural["beat"], binaural_carrier_hz=binaural["carrier"],
            nature_sounds=profile["nature"], lofi_tempo_bpm=profile["lofi_tempo"],
            lofi_key=profile["lofi_key"], meditation_type=profile["meditation"],
            description=profile["description"], should_transition=should,
            transition_seconds=SOUND_TRANSITION_SECONDS, target_quadrant=sq,
        )

    def _compute_psychologist_context(self, emotion, fine_emotion=None):
        now = time.time()
        history = list(self._history)
        vt = [e.smoothed_valence for e in history]
        at = [e.smoothed_arousal for e in history]
        recent = [e for e in history if now - e.timestamp < EMOTION_TREND_WINDOW]

        if len(recent) >= 4:
            fh = np.mean([e.smoothed_valence for e in recent[:len(recent)//2]])
            sh = np.mean([e.smoothed_valence for e in recent[len(recent)//2:]])
            d = sh - fh
            trend = "improving" if d > 0.1 else "declining" if d < -0.1 else "stable"
        else:
            trend = "insufficient_data"

        dominant = emotion.basic_emotion
        if recent:
            ec = {}
            for e in recent:
                ec[e.basic_emotion] = ec.get(e.basic_emotion, 0) + 1
            dominant = max(ec, key=ec.get)

        crisis = False
        crisis_reason = None
        cw = [e for e in history if now - e.timestamp < CRISIS_DURATION_SECONDS]
        if len(cw) >= 10:
            av = np.mean([e.smoothed_valence for e in cw])
            if av < CRISIS_VALENCE_THRESHOLD:
                crisis = True
                crisis_reason = f"Sustained negative valence ({av:.2f}) for {CRISIS_DURATION_SECONDS}s."

        if crisis:
            rec = "User shows sustained distress. Approach with empathy."
        elif trend == "declining":
            rec = "Emotional state declining. Explore stressors gently."
        elif trend == "improving":
            rec = "Emotional state improving. Reinforce positive trajectory."
        else:
            rec = "Stable. Continue current approach."

        return PsychologistContext(
            current_emotion=emotion.to_dict(),
            fine_grained_emotion=fine_emotion.to_dict() if fine_emotion else None,
            emotion_trend=trend,
            dominant_emotion_5min=dominant, valence_trajectory=vt, arousal_trajectory=at,
            crisis_flag=crisis, crisis_reason=crisis_reason,
            session_duration_seconds=now - self._session_start, recommendation=rec,
        )

    def get_emotion_history(self, seconds=60):
        now = time.time()
        return [e.to_dict() for e in self._history if now - e.timestamp < seconds]

    def reset_session(self):
        self._history.clear()
        self._session_start = time.time()
        self._smooth_valence = self._smooth_arousal = 0.0
        self._current_quadrant = "HV_LA"
        self._quadrant_since = time.time()
