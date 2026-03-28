package com.verite.tmr.data.models

import com.google.gson.annotations.SerializedName

// ── Flashcard Study ──────────────────────────────────────────────────────────

data class StartStudyRequest(val shuffle: Boolean = true)

data class StartStudyResponse(
    @SerializedName("n_concepts") val nConcepts: Int,
    val concepts: List<String>,
    val status: String,
)

data class FlashCard(
    val done: Boolean = false,
    val index: Int = 0,
    @SerializedName("n_remaining") val nRemaining: Int = 0,
    @SerializedName("n_total") val nTotal: Int = 0,
    val concept: String = "",
    val category: String = "",
    val difficulty: String = "",
    val definition: String = "",
    @SerializedName("shown_at") val shownAt: Double = 0.0,
)

data class CardAnswerRequest(
    @SerializedName("concept_key") val conceptKey: String,
    val rating: Int,
    @SerializedName("shown_at") val shownAt: Double,
)

data class CardAnswerResponse(
    val concept: String = "",
    val strength: Double = 0.0,
    val tier: String = "",
    val correct: Int = 0,
    @SerializedName("rt_s") val rtS: Double = 0.0,
    @SerializedName("sweet_spot") val sweetSpot: Boolean = false,
)

data class StudyCompleteResponse(
    @SerializedName("n_studied") val nStudied: Int = 0,
    @SerializedName("n_sweet_spot") val nSweetSpot: Int = 0,
    @SerializedName("n_cues_queued") val nCuesQueued: Int = 0,
    val strengths: Map<String, Double> = emptyMap(),
    @SerializedName("audio_backend") val audioBackend: String = "",
    val message: String = "",
)

// ── Quiz ─────────────────────────────────────────────────────────────────────

data class StartQuizRequest(
    val mode: String = "pre_sleep",
    @SerializedName("n_questions") val nQuestions: Int = 10,
)

data class StartQuizResponse(
    val mode: String = "",
    @SerializedName("n_questions") val nQuestions: Int = 0,
    val status: String = "",
)

data class QuizQuestion(
    val done: Boolean = false,
    val index: Int = 0,
    @SerializedName("n_remaining") val nRemaining: Int = 0,
    @SerializedName("n_total") val nTotal: Int = 0,
    val concept: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    @SerializedName("shown_at") val shownAt: Double = 0.0,
)

data class QuizAnswerRequest(
    @SerializedName("concept_key") val conceptKey: String,
    @SerializedName("selected_index") val selectedIndex: Int,
    @SerializedName("shown_at") val shownAt: Double,
)

data class QuizAnswerResponse(
    val concept: String = "",
    val correct: Boolean = false,
    @SerializedName("correct_index") val correctIndex: Int = -1,
    @SerializedName("selected_index") val selectedIndex: Int = -1,
    @SerializedName("rt_s") val rtS: Double = 0.0,
    val done: Boolean = false,
    @SerializedName("n_remaining") val nRemaining: Int = 0,
)

data class QuizCompleteResponse(
    val mode: String = "",
    @SerializedName("n_correct") val nCorrect: Int = 0,
    @SerializedName("n_total") val nTotal: Int = 0,
    @SerializedName("accuracy_pct") val accuracyPct: Double = 0.0,
    @SerializedName("avg_rt_s") val avgRtS: Double = 0.0,
    @SerializedName("weight_updates") val weightUpdates: Int = 0,
)

// ── Audio ────────────────────────────────────────────────────────────────────

data class ConceptAudioItem(
    val key: String = "",
    val definition: String = "",
    val category: String = "",
    @SerializedName("study_url") val studyUrl: String = "",
    @SerializedName("definition_url") val definitionUrl: String = "",
    @SerializedName("cue_url") val cueUrl: String = "",
)

data class AudioConceptsResponse(
    val concepts: List<ConceptAudioItem> = emptyList(),
    @SerializedName("audio_backend") val audioBackend: String = "",
)

// ── UI State ─────────────────────────────────────────────────────────────────

data class StudyUiState(
    val active: Boolean = false,
    val currentCard: FlashCard? = null,
    val isFlipped: Boolean = false,
    val lastAnswer: CardAnswerResponse? = null,
    val nStudied: Int = 0,
    val nTotal: Int = 0,
)

data class QuizUiState(
    val active: Boolean = false,
    val mode: String = "pre_sleep",
    val currentQuestion: QuizQuestion? = null,
    val lastAnswer: QuizAnswerResponse? = null,
    val showingFeedback: Boolean = false,
    val nAnswered: Int = 0,
    val nCorrect: Int = 0,
    val nTotal: Int = 0,
)

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentConcept: String = "",
    val positionMs: Int = 0,
    val durationMs: Int = 0,
)
