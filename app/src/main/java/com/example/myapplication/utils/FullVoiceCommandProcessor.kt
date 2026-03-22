package com.example.myapplication.utils

import android.util.Log
import com.example.myapplication.Secrets
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
 * FullVoiceCommandProcessor — Comprehensive voice command engine for Vérité.
 *
 * Two-tier classification:
 * 1. Fast local regex matching for common commands (0ms latency)
 * 2. LLM fallback via Groq for natural language understanding (~500ms)
 *
 * Supports controlling ALL app features including sleep, sound, TMR,
 * device management, navigation, tasks, habits, bedtime, reports, and AI chat.
 */
object FullVoiceCommandProcessor {

    private const val TAG = "FullVoiceCmdProcessor"

    // ── Extended Intent Enum ─────────────────────────────────────────────

    enum class FullIntent {
        // Tasks
        ADD_TASK, COMPLETE_TASK, DELETE_TASK, LIST_TASKS, UPDATE_TASK_PRIORITY,
        // Habits
        ADD_HABIT, TOGGLE_HABIT, DELETE_HABIT, QUERY_STREAK, LIST_HABITS,
        // Sleep & Sound
        START_SLEEP_SESSION, STOP_SLEEP_SESSION,
        PLAY_SLEEP_SOUND, PLAY_FOCUS_SOUND, PLAY_RELAX_SOUND, PLAY_MEDITATE_SOUND,
        STOP_SOUND, SET_VOLUME,
        // TMR (Learning)
        START_TMR_SESSION, GENERATE_FLASHCARDS, START_QUIZ,
        // Device Control
        CONNECT_DEVICE, DISCONNECT_DEVICE, SET_TEMPERATURE, TOGGLE_VIBRATION,
        CHECK_BATTERY, TOGGLE_SENSOR,
        // Navigation
        NAVIGATE_DASHBOARD, NAVIGATE_SLEEP_DATA, NAVIGATE_BIOFEEDBACK,
        NAVIGATE_SETTINGS, NAVIGATE_PROFILE, NAVIGATE_ALARM,
        NAVIGATE_TODO, NAVIGATE_MORNING_BRIEF, NAVIGATE_TMR,
        NAVIGATE_SOUND, NAVIGATE_ANTIGRAVITY, NAVIGATE_DREAM_JOURNAL,
        NAVIGATE_DEVICES, NAVIGATE_REPORTS, NAVIGATE_DAILY_PROGRESS,
        // Bedtime
        ADD_BEDTIME_ITEM, TOGGLE_BEDTIME_ITEM, START_BEDTIME_ROUTINE,
        // Reports & Analytics
        SHOW_DAILY_PROGRESS, SHOW_SAVED_REPORTS, SHOW_ANALYTICS,
        // AI Chat
        ASK_AI_QUESTION, START_RECOVERY_PLAN,
        // Voice Agent
        CHANGE_VOICE, IDENTIFY_USER, VOICE_SETTINGS,
        // Proactive
        WHATS_NEXT, MORNING_SUMMARY, GOODNIGHT,
        // Unknown
        UNKNOWN
    }

    data class FullCommandResult(
        val intent: FullIntent,
        val confidence: Float,
        val entityName: String? = null,
        val priority: String? = null,
        val category: String? = null,
        val parameters: Map<String, String> = emptyMap(),
        val rawText: String = ""
    )

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Process voice input. Tries local parsing first; falls back to LLM if needed.
     */
    suspend fun process(input: String): FullCommandResult {
        val text = input.lowercase().trim()

        // 1. Try fast local matching first
        val localResult = parseLocal(text)
        if (localResult.intent != FullIntent.UNKNOWN && localResult.confidence >= 0.8f) {
            return localResult.copy(rawText = input)
        }

        // 2. LLM fallback for natural language
        val llmResult = parseLlm(input)
        if (llmResult != null && llmResult.intent != FullIntent.UNKNOWN) {
            return llmResult.copy(rawText = input)
        }

        // 3. If LLM failed, return local result even if low confidence, or UNKNOWN
        return localResult.copy(rawText = input)
    }

    /**
     * Get contextual suggestions based on current app state.
     */
    fun getSuggestions(
        pendingTaskCount: Int,
        habitsCompletedToday: Boolean,
        deviceConnected: Boolean,
        timeOfDay: String // "morning", "afternoon", "evening", "night"
    ): List<String> {
        val suggestions = mutableListOf<String>()

        when (timeOfDay) {
            "morning" -> {
                suggestions.add("Hey Vérité, give me my morning summary")
                if (pendingTaskCount > 0) suggestions.add("What tasks do I have today?")
                suggestions.add("Start a focus session")
            }
            "afternoon" -> {
                suggestions.add("Show me my daily progress")
                if (!habitsCompletedToday) suggestions.add("What habits haven't I done?")
                suggestions.add("Play some focus sounds")
            }
            "evening" -> {
                suggestions.add("Start my bedtime routine")
                suggestions.add("Play relax sounds")
                suggestions.add("Open my dream journal")
            }
            "night" -> {
                suggestions.add("Start my sleep session")
                suggestions.add("Play sleep sounds")
                suggestions.add("Good night, Vérité")
            }
        }

        if (!deviceConnected) {
            suggestions.add("Connect my sleep band")
        }

        return suggestions.take(4)
    }

    // ── Local Parser (regex-based) ───────────────────────────────────────

    private fun parseLocal(text: String): FullCommandResult {
        // ── TASKS ──
        if (matchesAny(text, listOf("add task", "create task", "new task"))) {
            val entity = extractAfter(text, listOf("add task", "create task", "new task", "called", "named"))
            val priority = extractPriority(text)
            val category = extractCategoryFromText(text)
            return FullCommandResult(FullIntent.ADD_TASK, 0.9f, entity, priority, category)
        }
        if (matchesAny(text, listOf("complete task", "finish task", "done with task", "mark task", "check off"))) {
            val entity = extractAfter(text, listOf("complete task", "finish task", "done with task", "mark task done", "mark task", "check off"))
            return FullCommandResult(FullIntent.COMPLETE_TASK, 0.9f, entity)
        }
        if (matchesAny(text, listOf("delete task", "remove task"))) {
            val entity = extractAfter(text, listOf("delete task", "remove task"))
            return FullCommandResult(FullIntent.DELETE_TASK, 0.9f, entity)
        }
        if (matchesAny(text, listOf("list tasks", "show tasks", "what tasks", "my tasks", "pending tasks"))) {
            return FullCommandResult(FullIntent.LIST_TASKS, 0.85f)
        }

        // ── HABITS ──
        if (matchesAny(text, listOf("add habit", "create habit", "new habit", "track habit"))) {
            val entity = extractAfter(text, listOf("add habit", "create habit", "new habit", "track habit", "called", "named"))
            return FullCommandResult(FullIntent.ADD_HABIT, 0.9f, entity)
        }
        if (matchesAny(text, listOf("toggle habit", "check habit", "complete habit", "did my", "i did"))) {
            val entity = extractAfter(text, listOf("toggle habit", "check habit", "complete habit", "did my", "i did"))
            return FullCommandResult(FullIntent.TOGGLE_HABIT, 0.85f, entity)
        }
        if (matchesAny(text, listOf("streak", "how many days", "habit streak"))) {
            val entity = extractAfter(text, listOf("streak for", "streak", "for"))
            return FullCommandResult(FullIntent.QUERY_STREAK, 0.85f, entity)
        }
        if (matchesAny(text, listOf("list habits", "show habits", "my habits", "what habits"))) {
            return FullCommandResult(FullIntent.LIST_HABITS, 0.85f)
        }

        // ── SLEEP & SOUND ──
        if (matchesAny(text, listOf("start sleep", "begin sleep", "sleep session", "going to sleep", "track my sleep"))) {
            return FullCommandResult(FullIntent.START_SLEEP_SESSION, 0.9f)
        }
        if (matchesAny(text, listOf("stop sleep", "end sleep", "wake up", "stop tracking"))) {
            return FullCommandResult(FullIntent.STOP_SLEEP_SESSION, 0.9f)
        }
        if (matchesAny(text, listOf("sleep sound", "play sleep", "sleep music"))) {
            return FullCommandResult(FullIntent.PLAY_SLEEP_SOUND, 0.9f)
        }
        if (matchesAny(text, listOf("focus sound", "play focus", "concentration", "focus music"))) {
            return FullCommandResult(FullIntent.PLAY_FOCUS_SOUND, 0.9f)
        }
        if (matchesAny(text, listOf("relax sound", "play relax", "relaxation", "relax music", "chill"))) {
            return FullCommandResult(FullIntent.PLAY_RELAX_SOUND, 0.9f)
        }
        if (matchesAny(text, listOf("meditat", "play meditat", "mindful", "zen"))) {
            return FullCommandResult(FullIntent.PLAY_MEDITATE_SOUND, 0.9f)
        }
        if (matchesAny(text, listOf("stop sound", "stop music", "pause music", "silence", "quiet"))) {
            return FullCommandResult(FullIntent.STOP_SOUND, 0.9f)
        }

        // ── TMR / LEARNING ──
        if (matchesAny(text, listOf("start learning", "learning session", "tmr session", "study session"))) {
            return FullCommandResult(FullIntent.START_TMR_SESSION, 0.9f)
        }
        if (matchesAny(text, listOf("flashcard", "flash card", "generate cards"))) {
            return FullCommandResult(FullIntent.GENERATE_FLASHCARDS, 0.9f)
        }
        if (matchesAny(text, listOf("start quiz", "quiz me", "test me"))) {
            return FullCommandResult(FullIntent.START_QUIZ, 0.9f)
        }

        // ── DEVICE CONTROL ──
        if (matchesAny(text, listOf("connect device", "pair device", "connect band", "connect sleep band", "find device"))) {
            return FullCommandResult(FullIntent.CONNECT_DEVICE, 0.9f)
        }
        if (matchesAny(text, listOf("disconnect device", "unpair", "disconnect band"))) {
            return FullCommandResult(FullIntent.DISCONNECT_DEVICE, 0.9f)
        }
        val tempMatch = Regex("(?:set |change |adjust )?temperature (?:to )?([0-9]+)").find(text)
        if (tempMatch != null) {
            return FullCommandResult(FullIntent.SET_TEMPERATURE, 0.9f, parameters = mapOf("temperature" to tempMatch.groupValues[1]))
        }
        if (matchesAny(text, listOf("vibration", "vibrate", "massage", "toggle vibration"))) {
            return FullCommandResult(FullIntent.TOGGLE_VIBRATION, 0.85f)
        }
        if (matchesAny(text, listOf("battery", "charge level", "how much charge", "battery level"))) {
            return FullCommandResult(FullIntent.CHECK_BATTERY, 0.85f)
        }

        // ── NAVIGATION ──
        if (matchesAny(text, listOf("go to dashboard", "open dashboard", "show dashboard", "home screen"))) {
            return FullCommandResult(FullIntent.NAVIGATE_DASHBOARD, 0.9f)
        }
        if (matchesAny(text, listOf("go to sleep", "open sleep", "sleep data", "show sleep"))) {
            return FullCommandResult(FullIntent.NAVIGATE_SLEEP_DATA, 0.85f)
        }
        if (matchesAny(text, listOf("biofeedback", "bio feedback", "sensor data", "heart rate"))) {
            return FullCommandResult(FullIntent.NAVIGATE_BIOFEEDBACK, 0.85f)
        }
        if (matchesAny(text, listOf("go to settings", "open settings", "preferences"))) {
            return FullCommandResult(FullIntent.NAVIGATE_SETTINGS, 0.9f)
        }
        if (matchesAny(text, listOf("go to profile", "open profile", "my profile", "account"))) {
            return FullCommandResult(FullIntent.NAVIGATE_PROFILE, 0.9f)
        }
        if (matchesAny(text, listOf("alarm", "set alarm", "wake up alarm", "open alarm"))) {
            return FullCommandResult(FullIntent.NAVIGATE_ALARM, 0.85f)
        }
        if (matchesAny(text, listOf("to do", "todo", "task list", "open tasks", "open todo"))) {
            return FullCommandResult(FullIntent.NAVIGATE_TODO, 0.85f)
        }
        if (matchesAny(text, listOf("morning brief", "morning summary", "daily brief"))) {
            return FullCommandResult(FullIntent.NAVIGATE_MORNING_BRIEF, 0.9f)
        }
        if (matchesAny(text, listOf("antigravity", "visualization", "particle"))) {
            return FullCommandResult(FullIntent.NAVIGATE_ANTIGRAVITY, 0.85f)
        }
        if (matchesAny(text, listOf("dream journal", "dreams", "log dream"))) {
            return FullCommandResult(FullIntent.NAVIGATE_DREAM_JOURNAL, 0.85f)
        }
        if (matchesAny(text, listOf("my devices", "device list", "show devices"))) {
            return FullCommandResult(FullIntent.NAVIGATE_DEVICES, 0.9f)
        }
        if (matchesAny(text, listOf("saved report", "my report", "show report"))) {
            return FullCommandResult(FullIntent.NAVIGATE_REPORTS, 0.85f)
        }
        if (matchesAny(text, listOf("daily progress", "today progress", "progress report"))) {
            return FullCommandResult(FullIntent.NAVIGATE_DAILY_PROGRESS, 0.85f)
        }

        // ── BEDTIME ──
        if (matchesAny(text, listOf("add bedtime", "bedtime item", "add to routine"))) {
            val entity = extractAfter(text, listOf("add bedtime item", "add bedtime", "add to routine"))
            return FullCommandResult(FullIntent.ADD_BEDTIME_ITEM, 0.85f, entity)
        }
        if (matchesAny(text, listOf("start bedtime", "bedtime routine", "begin routine"))) {
            return FullCommandResult(FullIntent.START_BEDTIME_ROUTINE, 0.9f)
        }

        // ── AI ──
        if (matchesAny(text, listOf("recovery plan", "start recovery", "quit", "break habit", "stop smoking", "addiction"))) {
            return FullCommandResult(FullIntent.START_RECOVERY_PLAN, 0.85f)
        }

        // ── VOICE AGENT ──
        if (matchesAny(text, listOf("change voice", "switch voice", "different voice"))) {
            return FullCommandResult(FullIntent.CHANGE_VOICE, 0.9f)
        }
        if (matchesAny(text, listOf("who am i", "identify me", "recognize me", "my identity"))) {
            return FullCommandResult(FullIntent.IDENTIFY_USER, 0.9f)
        }
        if (matchesAny(text, listOf("voice settings", "voice options", "voice config"))) {
            return FullCommandResult(FullIntent.VOICE_SETTINGS, 0.9f)
        }

        // ── PROACTIVE ──
        if (matchesAny(text, listOf("what's next", "what should i do", "suggest something", "what now"))) {
            return FullCommandResult(FullIntent.WHATS_NEXT, 0.85f)
        }
        if (matchesAny(text, listOf("good morning", "morning summary", "morning brief"))) {
            return FullCommandResult(FullIntent.MORNING_SUMMARY, 0.9f)
        }
        if (matchesAny(text, listOf("good night", "goodnight", "going to bed", "night night"))) {
            return FullCommandResult(FullIntent.GOODNIGHT, 0.9f)
        }

        if (text.length > 10 && (text.contains("?") || text.startsWith("what") || text.startsWith("how") || text.startsWith("why") || text.startsWith("tell me") || text.startsWith("explain"))) {
            return FullCommandResult(FullIntent.ASK_AI_QUESTION, 0.7f, entityName = text)
        }

        return FullCommandResult(FullIntent.UNKNOWN, 0.0f)
    }

    // ── LLM Parser (Groq) ──────────────────────────────────────────────

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private suspend fun parseLlm(input: String): FullCommandResult? = withContext(Dispatchers.IO) {
        try {
            val intents = FullIntent.entries.joinToString(", ") { it.name }
            val prompt = """
You are the Vérité voice assistant intent classifier. Given a user voice command, identify the intent and any entities.

Available intents: $intents

Respond ONLY with a JSON object:
{"intent":"INTENT_NAME","confidence":0.0-1.0,"entity_name":"extracted entity or null","priority":"High|Medium|Low or null","category":"Work|Personal|Health|Finance|Errand|Learning|Social or null","parameters":{}}

User command: "$input"
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${Secrets.GROQ_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "LLM classification failed: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val rawContent = json.getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")

                val start = rawContent.indexOf("{")
                val end = rawContent.lastIndexOf("}")
                if (start == -1 || end == -1) return@withContext null

                val result = JSONObject(rawContent.substring(start, end + 1))
                val intentStr = result.optString("intent", "UNKNOWN")
                val intent = try { FullIntent.valueOf(intentStr) } catch (_: Exception) { FullIntent.UNKNOWN }
                val confidence = result.optDouble("confidence", 0.5).toFloat()
                val entityName = result.optString("entity_name").takeIf { it != "null" && it.isNotBlank() }
                val priority = result.optString("priority").takeIf { it != "null" && it.isNotBlank() }
                val category = result.optString("category").takeIf { it != "null" && it.isNotBlank() }

                val params = mutableMapOf<String, String>()
                val paramsObj = result.optJSONObject("parameters")
                paramsObj?.keys()?.forEach { key ->
                    params[key] = paramsObj.optString(key, "")
                }

                FullCommandResult(intent, confidence, entityName, priority, category, params)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM classification error: ${e.message}")
            null
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun matchesAny(text: String, patterns: List<String>): Boolean {
        return patterns.any { text.contains(it) }
    }

    private fun extractAfter(text: String, keywords: List<String>): String? {
        var result = text
        keywords.forEach { kw ->
            if (result.contains(kw)) {
                result = result.substringAfter(kw).trim()
            }
        }
        return result
            .split(" for ", " with ", " in ", " at ", " to ")
            .first().trim()
            .takeIf { it.isNotBlank() && it.length > 1 }
    }

    private fun extractPriority(text: String): String? {
        return when {
            text.contains("high priority") || text.contains("urgent") -> "High"
            text.contains("medium priority") -> "Medium"
            text.contains("low priority") || text.contains("not urgent") -> "Low"
            else -> null
        }
    }

    private fun extractCategoryFromText(text: String): String? {
        val categories = mapOf(
            "work" to "Work", "personal" to "Personal", "health" to "Health",
            "finance" to "Finance", "errand" to "Errand", "learning" to "Learning",
            "social" to "Social"
        )
        categories.forEach { (key, value) ->
            if (text.contains(key)) return value
        }
        return null
    }
}
