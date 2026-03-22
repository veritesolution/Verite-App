package com.example.myapplication.data.logic

data class EmotionState(
    val timestamp: Double,
    val valence: Double,
    val arousal: Double,
    val quadrant: String,
    val valence_confidence: Double,
    val arousal_confidence: Double,
    val basic_emotion: String,
    val basic_emotion_prob: Double,
    val basic_emotion_probs: Map<String, Double>,
    val signal_quality: Double,
    val faa: Double,
    val smoothed_valence: Double,
    val smoothed_arousal: Double,
    val smoothed_quadrant: String,
    val valence_reliability: String,
    val arousal_reliability: String,
    val is_calibrated: Boolean,
    val low_quality_warning: Boolean
)

data class SoundRecommendation(
    val binaural: BinauralConfig,
    val nature_sounds: List<String>,
    val lofi: LofiConfig,
    val meditation: String?,
    val description: String,
    val should_transition: Boolean,
    val transition_seconds: Double,
    val target_quadrant: String,
    val disclaimer: String
)

data class BinauralConfig(val beat_hz: Double, val carrier_hz: Double)
data class LofiConfig(val tempo_bpm: Int, val key: String)

data class PsychologistContext(
    val current: Map<String, Any>,
    val trend: String,
    val dominant_5min: String,
    val valence_history: List<Double>,
    val arousal_history: List<Double>,
    val crisis: CrisisInfo,
    val session_seconds: Double,
    val recommendation: String,
    val reliability_note: String
)

data class CrisisInfo(val flag: Boolean, val reason: String?)
