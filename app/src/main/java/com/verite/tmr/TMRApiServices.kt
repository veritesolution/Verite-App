package com.verite.tmr

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// ─── Groq API (Primary — 14,400 req/day free) ──────────────────────
// Base URL: https://api.groq.com/openai/v1/
interface GroqApiService {

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: GroqRequest
    ): Response<GroqResponse>
}

// ─── Gemini API (Backup — 250 req/day free) ─────────────────────────
// Base URL: https://generativelanguage.googleapis.com/v1beta/
//
// FIX: Changed @Path to @Query — the API key is a query parameter,
// not a path segment. @Path would break URL encoding and was a bug.
interface GeminiApiService {

    @POST("models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
