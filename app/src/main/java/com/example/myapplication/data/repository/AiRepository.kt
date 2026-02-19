package com.example.myapplication.data.repository

import com.example.myapplication.data.model.AiRequest
import com.example.myapplication.data.model.AiResponse
import com.example.myapplication.data.model.Message
import com.example.myapplication.data.remote.AiApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiRepository {
    private val apiKey = "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"
    
    private val apiService: AiApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://openrouter.ai/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(AiApiService::class.java)
    }

    private val PRIMARY_MODEL = "deepseek/deepseek-r1-0528:free"
    private val SECONDARY_MODEL = "deepseek/deepseek-chat"
    private val TERTIARY_MODEL = "mistralai/mistral-7b-instruct:free"
    private val MAX_RETRIES = 2
    private val RETRY_DELAY = 1500L // milliseconds

    fun generate21DayPlan(
        addictionType: String,
        frequency: String,
        reasonForAddiction: String,
        duration: String,
        reasonForStopping: String
    ): Flow<Result<String>> = flow {
        val prompt = """
            You are a world-class recovery specialist and behavioral psychologist. 
            Generate a highly personalized 21-day recovery plan based on the following psychological profile:
            
                 Please structure your response as follows:
            1. Highly empathetic and clinical feedback on the user's situation based on human psychology.
            2. A day-by-day 21-day plan. Each day should include:
               - Focus: A psychological grounding or mental shift.
               - Action: A specific, practical task to perform.
            
            - Addiction Type: $addictionType
            - Current Frequency: $frequency
            - Root Cause: $reasonForAddiction
            - Addiction Duration: $duration
            - Personal Motivation for Change: $reasonForStopping
            
       
               - Motivation: A powerful quote or thought related to $addictionType recovery.
            3. General Practice Tips: 3-5 specific tips for managing cravings for $addictionType.
            
            Ensure the tasks are realistic, progressive, and grounded in cognitive behavioral therapy (CBT) principles.
        """.trimIndent()

        var lastResponse: AiResponse? = null
        var lastError: Exception? = null

        // 🔁 TRY PRIMARY MODEL
        for (attempt in 1..MAX_RETRIES) {
            try {
                val request = AiRequest(
                    model = PRIMARY_MODEL,
                    messages = listOf(
                        Message(role = "system", content = "You are a recovery specialist and empathetic counselor."),
                        Message(role = "user", content = prompt)
                    )
                )

                val response = apiService.generatePlan(
                    authorization = "Bearer $apiKey",
                    referer = "https://verite.app",
                    title = "Verite App",
                    request = request
                )
                
                val content = response.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    emit(Result.success(content))
                    return@flow
                } else {
                    lastResponse = response
                    val errorMsg = response.error?.message ?: "Primary model busy (attempt $attempt)"
                    println(errorMsg)
                    if (attempt < MAX_RETRIES) kotlinx.coroutines.delay(RETRY_DELAY)
                }
            } catch (e: Exception) {
                lastError = e
                println("⚠️ Attempt $attempt error: ${e.message}")
                if (attempt < MAX_RETRIES) kotlinx.coroutines.delay(RETRY_DELAY)
            }
        }

        // 🚀 FALLBACK CHAIN
        val fallbacks = listOf(SECONDARY_MODEL, TERTIARY_MODEL)
        for (fallback in fallbacks) {
            try {
                println("⚡ Switching to fallback model: $fallback")
                val fallbackRequest = AiRequest(
                    model = fallback,
                    messages = listOf(
                        Message(role = "system", content = "You are a recovery specialist and empathetic counselor."),
                        Message(role = "user", content = prompt)
                    )
                )

                val response = apiService.generatePlan(
                    authorization = "Bearer $apiKey",
                    referer = "https://verite.app",
                    title = "Verite App",
                    request = fallbackRequest
                )
                
                val content = response.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    emit(Result.success(content))
                    return@flow
                }
            } catch (e: Exception) {
                println("⚠️ Fallback $fallback failed: ${e.message}")
            }
        }

        // Final failure if all models failed
        val failureMsg = "AI services are currently busy or unavailable. Please try again in a few minutes."
        println("❌ All models failed. Emitting failure.")
        emit(Result.failure(Exception(failureMsg)))
    }
}
