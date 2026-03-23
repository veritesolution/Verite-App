// ═══════════════════════════════════════════════════════════════
// Retrofit interface for Verite Psychologist REST API
// ═══════════════════════════════════════════════════════════════

package com.example.myapplication.data.remote

import com.example.myapplication.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface PsychApiService {

    // ── Auth ─────────────────────────────────────────────

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: PsychRegisterRequest): Response<PsychTokenResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: PsychLoginRequest): Response<PsychTokenResponse>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: PsychRefreshRequest): Response<PsychTokenResponse>

    // ── Chat ─────────────────────────────────────────────

    @POST("api/v1/chat/message")
    suspend fun sendMessage(@Body request: PsychChatMessageRequest): Response<PsychChatMessageResponse>

    @POST("api/v1/chat/session")
    suspend fun createSession(): Response<PsychSessionCreateResponse>

    @GET("api/v1/chat/sessions")
    suspend fun listSessions(): Response<PsychSessionListResponse>

    @GET("api/v1/chat/session/{sessionId}")
    suspend fun getSession(@Path("sessionId") sessionId: String): Response<Map<String, Any>>

    @DELETE("api/v1/chat/session/{sessionId}")
    suspend fun deleteSession(@Path("sessionId") sessionId: String): Response<Map<String, String>>

    // ── Feedback ─────────────────────────────────────────

    @POST("api/v1/feedback")
    suspend fun submitFeedback(@Body request: PsychFeedbackRequest): Response<PsychFeedbackResponse>

    // ── Health ───────────────────────────────────────────

    @GET("api/v1/health")
    suspend fun healthCheck(): Response<PsychHealthResponse>
}
