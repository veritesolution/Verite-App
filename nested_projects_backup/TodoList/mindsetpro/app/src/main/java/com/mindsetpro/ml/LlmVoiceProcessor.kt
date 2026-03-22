package com.mindsetpro.ml

import com.mindsetpro.data.model.Intent
import com.mindsetpro.data.model.VoiceCommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LLM Fallback — Anthropic Claude Haiku integration.
 *
 * Called by VoiceCommandProcessor when regex confidence < 0.7.
 * Sends the user's voice text to Claude Haiku for intent classification.
 *
 * Requires ANTHROPIC_API_KEY in BuildConfig or passed at init.
 */
class LlmVoiceProcessor(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-haiku-4-5-20251001"

        private val SYSTEM_PROMPT = """
            You are a voice command classifier for a habit & task management app called MindSet Pro.
            
            Given user voice input, classify it into exactly ONE intent and extract entities.
            
            Valid intents:
            - add_task: User wants to create a new task
            - complete_task: User wants to mark a task as done
            - delete_task: User wants to remove a task
            - add_habit: User wants to start tracking a new habit
            - toggle_habit: User completed/did a habit today
            - query_streak: User asks about their streak for a habit
            - query_tasks: User wants to see their tasks
            - list_habits: User wants to see their habits
            - unknown: Cannot determine intent
            
            Respond ONLY in this exact JSON format (no markdown, no backticks):
            {"intent":"<intent>","confidence":<0.0-1.0>,"entity_name":"<name or null>","priority":"<High|Medium|Low or null>","category":"<Health|Work|Learning|Mindfulness|Personal|Finance or null>"}
        """.trimIndent()
    }

    /**
     * Send voice text to Claude Haiku for intent classification.
     * Returns null on failure (caller should handle gracefully).
     */
    suspend fun classify(voiceText: String): VoiceCommandResult? = withContext(Dispatchers.IO) {
        try {
            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Classify this voice command: \"$voiceText\"")
                })
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", 200)
                put("system", SYSTEM_PROMPT)
                put("messages", messagesArray)
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val responseBody = response.body?.string() ?: return@withContext null
            val responseJson = JSONObject(responseBody)

            // Extract text content from Claude's response
            val content = responseJson.getJSONArray("content")
            val text = content.getJSONObject(0).getString("text").trim()

            // Parse the JSON response
            val parsed = JSONObject(text)
            val intentStr = parsed.getString("intent")
            val confidence = parsed.getDouble("confidence").toFloat()
            val entityName = parsed.optString("entity_name").takeIf { it != "null" && it.isNotBlank() }
            val priority = parsed.optString("priority").takeIf { it != "null" && it.isNotBlank() }
            val category = parsed.optString("category").takeIf { it != "null" && it.isNotBlank() }

            val intent = try {
                Intent.valueOf(intentStr.uppercase())
            } catch (_: Exception) {
                Intent.UNKNOWN
            }

            VoiceCommandResult(
                intent = intent,
                confidence = confidence,
                entityName = entityName,
                priority = priority,
                category = category
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
