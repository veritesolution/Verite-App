package com.example.myapplication.data.logic

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PsychologistEmotionBridge(private val apiBaseUrl: String, private val apiKey: String) {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun getEmotionContext(): PsychologistContext? {
        val request = Request.Builder()
            .url("$apiBaseUrl/api/v1/psychologist/context")
            .addHeader("X-API-Key", apiKey)
            .build()
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                gson.fromJson(response.body?.string(), PsychologistContext::class.java)
            } catch (e: Exception) { null }
        }
    }

    /** Inject into AI psychologist system prompt before each response. */
    fun buildSystemPromptInjection(ctx: PsychologistContext): String {
        return """
        [REAL-TIME EMOTION DATA — ${ctx.reliability_note}]
        Emotion: ${(ctx.current["basic_emotion"] as? String) ?: "unknown"}
        Valence: ${ctx.current["smoothed_valence"]} (${if ((ctx.current["smoothed_valence"] as? Double ?: 0.0) > 0) "positive" else "negative"})
        Arousal: ${ctx.current["smoothed_arousal"]}
        Calibrated: ${ctx.current["is_calibrated"]}
        Signal quality: ${ctx.current["signal_quality"]}
        ${if (ctx.current["low_quality_warning"] == true) "⚠️ LOW SIGNAL QUALITY — electrode contact may be poor" else ""}
        Trend: ${ctx.trend}
        Dominant (5min): ${ctx.dominant_5min}
        ${if (ctx.crisis.flag) "⚠️ CRISIS: ${ctx.crisis.reason}" else ""}

        [RECOMMENDATION] ${ctx.recommendation}

        Adapt your response. If emotion contradicts words, gently explore.
        Do NOT mention the EEG device or technical details to the user.
        """.trimIndent()
    }
}
