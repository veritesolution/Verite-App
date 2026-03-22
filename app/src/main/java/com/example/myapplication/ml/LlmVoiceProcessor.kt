package com.example.myapplication.ml

import com.example.myapplication.data.model.Intent
import com.example.myapplication.data.model.VoiceCommandResult
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
 * LLM Fallback — AI-powered intent classification.
 * Adaptive for OpenRouter/Anthropic.
 */
class LlmVoiceProcessor(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        // Using OpenRouter as a bridge (configured in build.gradle)
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val MODEL = "anthropic/claude-3-haiku"

        private val SYSTEM_PROMPT = """
            You are a voice command classifier for MindSet Pro.
            Classify user input into ONE intent: add_task, complete_task, delete_task, add_habit, toggle_habit, query_streak, query_tasks, list_habits.
            Respond ONLY in JSON: {"intent":"<intent>","confidence":<0.0-1.0>,"entity_name":"<name>","priority":"<High|Medium|Low>","category":"<Category>"}
        """.trimIndent()
    }

    suspend fun classify(voiceText: String): VoiceCommandResult? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        try {
            val body = JSONObject().apply {
                put("model", MODEL)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", voiceText)
                    })
                }
                put("messages", messages)
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val responseBody = response.body?.string() ?: return@withContext null
            val responseJson = JSONObject(responseBody)
            val choices = responseJson.getJSONArray("choices")
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content")

            val parsed = JSONObject(content.trim())
            val intentStr = parsed.getString("intent").uppercase()
            val confidence = parsed.getDouble("confidence").toFloat()
            val entityName = parsed.optString("entity_name").takeIf { it != "null" && it.isNotBlank() }
            val priority = parsed.optString("priority").takeIf { it != "null" && it.isNotBlank() }
            val category = parsed.optString("category").takeIf { it != "null" && it.isNotBlank() }

            VoiceCommandResult(
                intent = try { Intent.valueOf(intentStr) } catch (_: Exception) { Intent.UNKNOWN },
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
