// ═══════════════════════════════════════════════════════════════
// File: data/remote/VeriteApiService.kt
// Retrofit interface for Verite REST API
// Add to: app/src/main/java/com/yourapp/data/remote/
// ═══════════════════════════════════════════════════════════════

package com.yourapp.data.remote

import com.yourapp.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface VeriteApiService {

    // ── Auth ─────────────────────────────────────────────

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<TokenResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<TokenResponse>

    // ── Chat ─────────────────────────────────────────────

    @POST("api/v1/chat/message")
    suspend fun sendMessage(@Body request: ChatMessageRequest): Response<ChatMessageResponse>

    @POST("api/v1/chat/session")
    suspend fun createSession(): Response<SessionCreateResponse>

    @GET("api/v1/chat/sessions")
    suspend fun listSessions(): Response<SessionListResponse>

    @GET("api/v1/chat/session/{sessionId}")
    suspend fun getSession(@Path("sessionId") sessionId: String): Response<Map<String, Any>>

    @DELETE("api/v1/chat/session/{sessionId}")
    suspend fun deleteSession(@Path("sessionId") sessionId: String): Response<Map<String, String>>

    // ── Feedback ─────────────────────────────────────────

    @POST("api/v1/feedback")
    suspend fun submitFeedback(@Body request: FeedbackRequest): Response<FeedbackResponse>

    // ── Health ───────────────────────────────────────────

    @GET("api/v1/health")
    suspend fun healthCheck(): Response<HealthResponse>
}
