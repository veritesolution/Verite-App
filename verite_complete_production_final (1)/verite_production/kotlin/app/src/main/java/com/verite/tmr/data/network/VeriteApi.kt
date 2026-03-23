package com.verite.tmr.data.network

import com.verite.tmr.data.models.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for the Vérité TMR REST API.
 *
 * Every endpoint mirrors main.py exactly:
 *   POST /session/start   → startSession()
 *   POST /session/stop    → stopSession()
 *   GET  /session/status  → getSessionStatus()
 *   GET  /config          → getConfig()
 *   POST /config          → updateConfig()
 *   POST /document        → uploadDocument()
 *   GET  /report          → getReport()
 *   GET  /health          → health()
 *
 * Authentication: X-API-Key header added automatically by the OkHttp interceptor
 * in VeriteClient — no need to add it here.
 */
interface VeriteApi {

    // ── Session lifecycle ────────────────────────────────────────────────────

    @POST("session/start")
    suspend fun startSession(
        @Body request: StartSessionRequest
    ): Response<StartSessionResponse>

    @POST("session/stop")
    suspend fun stopSession(): Response<StopSessionResponse>

    @GET("session/status")
    suspend fun getSessionStatus(): Response<SessionStatusResponse>

    // ── Config ───────────────────────────────────────────────────────────────

    @GET("config")
    suspend fun getConfig(): Response<Map<String, Any>>

    @POST("config")
    suspend fun updateConfig(
        @Body request: ConfigRequest
    ): Response<ConfigResponse>

    // ── Document upload ───────────────────────────────────────────────────────

    /** Upload a PDF / DOCX / TXT / PPTX for concept extraction. */
    @Multipart
    @POST("document")
    suspend fun uploadDocument(
        @Part file: MultipartBody.Part
    ): Response<DocumentResponse>

    // ── Report ────────────────────────────────────────────────────────────────

    @GET("report")
    suspend fun getReport(): Response<SessionReport>

    // ── Health ────────────────────────────────────────────────────────────────

    @GET("health")
    suspend fun health(): Response<Map<String, Any>>
}
