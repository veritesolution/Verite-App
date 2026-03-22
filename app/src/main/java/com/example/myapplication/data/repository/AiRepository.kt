package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.BuildConfig
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

class AiRepository {
    // API key loaded securely from BuildConfig (set in local.properties)
    private val apiKey = BuildConfig.OPENROUTER_API_KEY
    
    private val apiService: AiApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // Only log request/response bodies in debug builds
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
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
        ailmentType: String,
        frequency: String,
        reasonForAilment: String,
        duration: String,
        reasonForStopping: String,
        emotionContext: String = ""
    ): Flow<Result<String>> = flow {
        val prompt = """$emotionContext
            You are a world-class recovery specialist and behavioral psychologist. 
            Generate a highly personalized 21-day recovery plan based on the following psychological profile:
            
                 Please structure your response as follows:
            1. Highly empathetic and clinical feedback on the user's situation based on human psychology.
            2. A day-by-day 21-day plan. Each day should include:
               - Focus: A psychological grounding or mental shift.
               - Action: A specific, practical task to perform.
            
            - Ailment Type: $ailmentType
            - Current Frequency: $frequency
            - Root Cause: $reasonForAilment
            - Ailment Duration: $duration
            - Personal Motivation for Change: $reasonForStopping
            
       
               - Motivation: A powerful quote or thought related to $ailmentType recovery.
            3. General Practice Tips: 3-5 specific tips for managing cravings for $ailmentType.
            
            Ensure the tasks are realistic, progressive, and grounded in cognitive behavioral therapy (CBT) principles.
        """.trimIndent()

        var lastResponse: AiResponse? = null
        var lastError: Exception? = null

        // Try primary model with retries
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
                    if (BuildConfig.DEBUG) Log.d(TAG, errorMsg)
                    if (attempt < MAX_RETRIES) kotlinx.coroutines.delay(RETRY_DELAY)
                }
            } catch (e: Exception) {
                lastError = e
                if (BuildConfig.DEBUG) Log.w(TAG, "Attempt $attempt error: ${e.message}")
                if (attempt < MAX_RETRIES) kotlinx.coroutines.delay(RETRY_DELAY)
            }
        }

        // Fallback chain
        val fallbacks = listOf(SECONDARY_MODEL, TERTIARY_MODEL)
        for (fallback in fallbacks) {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "Switching to fallback model: $fallback")
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
                if (BuildConfig.DEBUG) Log.w(TAG, "Fallback $fallback failed: ${e.message}")
            }
        }

        // Final failure if all models failed
        val failureMsg = "AI services are currently busy or unavailable. Please try again in a few minutes."
        if (BuildConfig.DEBUG) Log.e(TAG, "All models failed. Emitting failure.")
        emit(Result.failure(Exception(failureMsg)))
    }

    companion object {
        private const val TAG = "AiRepository"
    }
}
