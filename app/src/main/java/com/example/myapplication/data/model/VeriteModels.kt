// ═══════════════════════════════════════════════════════════════
// Verite Psychologist API data classes
// ═══════════════════════════════════════════════════════════════

package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

// ── Auth ─────────────────────────────────────────────────────

data class PsychRegisterRequest(
    val username: String,
    val password: String,
    @SerializedName("display_name") val displayName: String? = null
)

data class PsychLoginRequest(
    val username: String,
    val password: String
)

data class PsychTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int
)

data class PsychRefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

// ── Chat ─────────────────────────────────────────────────────

data class PsychChatMessageRequest(
    val message: String,
    @SerializedName("session_id") val sessionId: String? = null
)

data class PsychChatMessageResponse(
    @SerializedName("session_id") val sessionId: String = "",
    val turn: Int = 0,
    val response: String = "",
    val safety: PsychSafetyInfo = PsychSafetyInfo(),
    val analysis: PsychAnalysisInfo = PsychAnalysisInfo(),
    val metrics: PsychMetricsInfo = PsychMetricsInfo(),
    @SerializedName("debug_metrics") val debugMetrics: PsychDebugMetrics? = null,
    val timestamp: String = ""
)

data class PsychCrisisResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("is_crisis") val isCrisis: Boolean,
    val message: String,
    val resources: List<PsychCrisisResource>,
    val timestamp: String
)

data class PsychSafetyInfo(
    @SerializedName("is_crisis") val isCrisis: Boolean = false,
    @SerializedName("crisis_method") val crisisMethod: String? = null,
    @SerializedName("toxicity_score") val toxicityScore: Float = 0f,
    @SerializedName("was_sanitized") val wasSanitized: Boolean = false,
    @SerializedName("harmful_advice_blocked") val harmfulAdviceBlocked: Boolean = false
)

data class PsychAnalysisInfo(
    val domain: String = "unknown",
    val phase: String = "intake",
    @SerializedName("emotional_intensity") val emotionalIntensity: Float = 0.5f,
    @SerializedName("distortions_detected") val distortionsDetected: List<String> = emptyList(),
    @SerializedName("therapeutic_move") val therapeuticMove: String = "validate",
    val reasoning: String = ""
)

data class PsychMetricsInfo(
    @SerializedName("json_parsed_ok") val jsonParsedOk: Boolean = false,
    @SerializedName("input_tokens") val inputTokens: Int = 0,
    @SerializedName("output_tokens") val outputTokens: Int = 0,
    @SerializedName("latency_ms") val latencyMs: Int = 0,
    @SerializedName("provider_used") val providerUsed: String = ""
)

data class PsychDebugMetrics(
    @SerializedName("style_empathy_similarity") val styleEmpathySimilarity: Float = -1f,
    @SerializedName("style_therapeutic_similarity") val styleTherapeuticSimilarity: Float = -1f,
    @SerializedName("relevance_passed") val relevancePassed: Boolean = true,
    @SerializedName("repetition_passed") val repetitionPassed: Boolean = true,
    val note: String = ""
)

data class PsychCrisisResource(
    val name: String = "",
    val contact: String = "",
    val type: String = "",
    val available: String = ""
)

// ── Session ──────────────────────────────────────────────────

data class PsychSessionCreateResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("created_at") val createdAt: String
)

data class PsychSessionListResponse(
    val sessions: List<PsychSessionSummary>,
    val total: Int
)

data class PsychSessionSummary(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("turn_count") val turnCount: Int,
    val domain: String,
    val phase: String,
    @SerializedName("crisis_flagged") val crisisFlagged: Boolean,
    @SerializedName("avg_empathy") val avgEmpathy: Float,
    @SerializedName("avg_coherence") val avgCoherence: Float
)

// ── Feedback ─────────────────────────────────────────────────

data class PsychFeedbackRequest(
    @SerializedName("session_id") val sessionId: String,
    val turn: Int,
    val rating: String,  // "helpful" or "not_helpful"
    val comment: String? = null
)

data class PsychFeedbackResponse(
    val status: String
)

// ── Health ───────────────────────────────────────────────────

data class PsychHealthResponse(
    val status: String,
    val version: String,
    @SerializedName("llm_provider") val llmProvider: String,
    @SerializedName("llm_available") val llmAvailable: Boolean,
    @SerializedName("uptime_seconds") val uptimeSeconds: Float
)

// ── WebSocket ────────────────────────────────────────────────

data class PsychWSAuthMessage(
    val token: String,
    @SerializedName("session_id") val sessionId: String? = null
)

data class PsychWSChatMessage(
    val type: String = "message",
    val content: String? = null
)

data class PsychWSResponse(
    val type: String,  // "response" | "crisis" | "error" | "session_created" | "pong"
    val content: String? = null,
    @SerializedName("session_id") val sessionId: String? = null,
    val analysis: PsychAnalysisInfo? = null,
    val safety: PsychSafetyInfo? = null,
    val metrics: PsychMetricsInfo? = null,
    val resources: List<PsychCrisisResource>? = null,
    val error: String? = null
)
