// ═══════════════════════════════════════════════════════════════
// File: data/model/VeriteModels.kt
// Verite API data classes for Kotlin/Android
// Add to: app/src/main/java/com/yourapp/data/model/
// ═══════════════════════════════════════════════════════════════

package com.yourapp.data.model

import com.google.gson.annotations.SerializedName

// ── Auth ─────────────────────────────────────────────────────

data class RegisterRequest(
    val username: String,
    val password: String,
    @SerializedName("display_name") val displayName: String? = null
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

// ── Chat ─────────────────────────────────────────────────────

data class ChatMessageRequest(
    val message: String,
    @SerializedName("session_id") val sessionId: String? = null
)

data class ChatMessageResponse(
    @SerializedName("session_id") val sessionId: String,
    val turn: Int,
    val response: String,
    val safety: SafetyInfo,
    val analysis: AnalysisInfo,
    val metrics: MetricsInfo,
    @SerializedName("debug_metrics") val debugMetrics: DebugMetrics? = null,
    val timestamp: String
)

data class CrisisResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("is_crisis") val isCrisis: Boolean,
    val message: String,
    val resources: List<CrisisResource>,
    val timestamp: String
)

data class SafetyInfo(
    @SerializedName("is_crisis") val isCrisis: Boolean = false,
    @SerializedName("crisis_method") val crisisMethod: String? = null,
    @SerializedName("toxicity_score") val toxicityScore: Float = 0f,
    @SerializedName("was_sanitized") val wasSanitized: Boolean = false,
    @SerializedName("harmful_advice_blocked") val harmfulAdviceBlocked: Boolean = false
)

data class AnalysisInfo(
    val domain: String = "unknown",
    val phase: String = "intake",
    @SerializedName("emotional_intensity") val emotionalIntensity: Float = 0.5f,
    @SerializedName("distortions_detected") val distortionsDetected: List<String> = emptyList(),
    @SerializedName("therapeutic_move") val therapeuticMove: String = "validate",
    val reasoning: String = ""
)

data class MetricsInfo(
    @SerializedName("json_parsed_ok") val jsonParsedOk: Boolean = false,
    @SerializedName("input_tokens") val inputTokens: Int = 0,
    @SerializedName("output_tokens") val outputTokens: Int = 0,
    @SerializedName("latency_ms") val latencyMs: Int = 0,
    @SerializedName("provider_used") val providerUsed: String = ""
)

data class DebugMetrics(
    @SerializedName("style_empathy_similarity") val styleEmpathySimilarity: Float = -1f,
    @SerializedName("style_therapeutic_similarity") val styleTherapeuticSimilarity: Float = -1f,
    @SerializedName("relevance_passed") val relevancePassed: Boolean = true,
    @SerializedName("repetition_passed") val repetitionPassed: Boolean = true,
    val note: String = ""
)

data class CrisisResource(
    val name: String,
    val contact: String,
    val type: String,
    val available: String
)

// ── Session ──────────────────────────────────────────────────

data class SessionCreateResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("created_at") val createdAt: String
)

data class SessionListResponse(
    val sessions: List<SessionSummary>,
    val total: Int
)

data class SessionSummary(
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

data class FeedbackRequest(
    @SerializedName("session_id") val sessionId: String,
    val turn: Int,
    val rating: String,  // "helpful" or "not_helpful"
    val comment: String? = null
)

data class FeedbackResponse(
    val status: String
)

// ── Health ───────────────────────────────────────────────────

data class HealthResponse(
    val status: String,
    val version: String,
    @SerializedName("llm_provider") val llmProvider: String,
    @SerializedName("llm_available") val llmAvailable: Boolean,
    @SerializedName("uptime_seconds") val uptimeSeconds: Float
)

// ── WebSocket ────────────────────────────────────────────────

data class WSAuthMessage(
    val token: String,
    @SerializedName("session_id") val sessionId: String? = null
)

data class WSChatMessage(
    val type: String = "message",
    val content: String? = null
)

data class WSResponse(
    val type: String,  // "response" | "crisis" | "error" | "session_created" | "pong"
    val content: String? = null,
    @SerializedName("session_id") val sessionId: String? = null,
    val analysis: AnalysisInfo? = null,
    val safety: SafetyInfo? = null,
    val metrics: MetricsInfo? = null,
    val resources: List<CrisisResource>? = null,
    val error: String? = null
)
