package com.example.myapplication.data.remote

import com.example.myapplication.data.model.AiRequest
import com.example.myapplication.data.model.AiResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AiApiService {
    @POST("api/v1/chat/completions")
    suspend fun generatePlan(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://verite.app",
        @Header("X-Title") title: String = "Verite App",
        @Body request: AiRequest
    ): AiResponse
}
