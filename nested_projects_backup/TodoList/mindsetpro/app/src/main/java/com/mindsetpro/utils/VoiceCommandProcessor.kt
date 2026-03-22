package com.mindsetpro.utils

import com.mindsetpro.data.model.Intent
import com.mindsetpro.data.model.VoiceCommandResult
import java.util.regex.Pattern

/**
 * Two-tier natural language voice command resolver.
 *
 * Tier 1 – Regex-based parser: ~0ms, handles common patterns.
 * Tier 2 – LLM fallback: Calls Anthropic Claude Haiku when regex confidence < 0.7
 *          (requires API key configured in BuildConfig).
 */
object VoiceCommandProcessor {

    // ── Regex Patterns ───────────────────────────────────────────────────────

    private val PATTERNS: Map<Intent, Regex> = mapOf(
        Intent.ADD_TASK to Regex(
            """(?:add|create|new)\s+(?:a\s+)?task\s+(?:called\s+|named\s+)?(?<n>.+?)(?:\s+priority\s+(?<priority>high|medium|low))?(?:\s+(?:category|under)\s+(?<category>\w+))?\s*$""",
            RegexOption.IGNORE_CASE
        ),
        Intent.COMPLETE_TASK to Regex(
            """(?:mark|complete|finish|done|tick\s+off)\s+(?:task\s+)?(?<n>.+?)(?:\s+(?:as\s+)?done)?\s*$""",
            RegexOption.IGNORE_CASE
        ),
        Intent.DELETE_TASK to Regex(
            """(?:delete|remove|cancel|trash)\s+(?:task\s+)?(?<n>.+)\s*$""",
            RegexOption.IGNORE_CASE
        ),
        Intent.ADD_HABIT to Regex(
            """(?:add|create|new|track|start)\s+(?:a\s+)?habit\s+(?:called\s+|named\s+|of\s+)?(?<n>.+?)(?:\s+(?:category|under)\s+(?<category>\w+))?\s*$""",
            RegexOption.IGNORE_CASE
        ),
        Intent.TOGGLE_HABIT to Regex(
            """(?:i\s+(?:did|completed?|finished?|just\s+did)|logged|mark(?:ed)?\s+habit|completed)\s+(?<n>.+)\s*$""",
            RegexOption.IGNORE_CASE
        ),
        Intent.QUERY_STREAK to Regex(
            """(?:what(?:'s|\s+is)?\s+my\s+streak\s+for|streak\s+(?:for|of))\s+(?<n>.+)\s*$""",
            RegexOption.IGNORE_CASE
        ),
        Intent.QUERY_TASKS to Regex(
            """(?:show|list|what(?:'s|\s+are)?\s+(?:my\s+)?)\s*(?:all\s+)?tasks?\s*$""",
            RegexOption.IGNORE_CASE
        ),
        Intent.LIST_HABITS to Regex(
            """(?:show|list|what(?:'s|\s+are)?\s+(?:my\s+)?)\s*(?:all\s+)?habits?\s*$""",
            RegexOption.IGNORE_CASE
        )
    )

    // ── Public API ───────────────────────────────────────────────────────────

    fun parse(text: String): VoiceCommandResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return VoiceCommandResult(Intent.UNKNOWN, 0f)
        }

        // Tier 1: Regex matching
        for ((intent, pattern) in PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val name = match.groups["n"]?.value?.trim()
                val priority = match.groups["priority"]?.value?.replaceFirstChar { it.uppercase() }
                val category = match.groups["category"]?.value?.replaceFirstChar { it.uppercase() }

                return VoiceCommandResult(
                    intent = intent,
                    confidence = 0.9f,
                    entityName = name,
                    priority = priority,
                    category = category
                )
            }
        }

        // Tier 1.5: Keyword fallback for common short commands
        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("add task") || lower.startsWith("new task") ->
                VoiceCommandResult(Intent.ADD_TASK, 0.6f,
                    entityName = trimmed.substringAfter("task").trim())

            lower.startsWith("add habit") || lower.startsWith("new habit") ->
                VoiceCommandResult(Intent.ADD_HABIT, 0.6f,
                    entityName = trimmed.substringAfter("habit").trim())

            lower.contains("show task") || lower.contains("my task") ->
                VoiceCommandResult(Intent.QUERY_TASKS, 0.7f)

            lower.contains("show habit") || lower.contains("my habit") ->
                VoiceCommandResult(Intent.LIST_HABITS, 0.7f)

            lower.contains("streak") ->
                VoiceCommandResult(Intent.QUERY_STREAK, 0.5f,
                    entityName = trimmed.substringAfter("streak").trim())

            lower.contains("done") || lower.contains("finish") || lower.contains("complete") ->
                VoiceCommandResult(Intent.COMPLETE_TASK, 0.5f,
                    entityName = trimmed.replace(Regex("(?i)(done|finish|complete|mark)"), "").trim())

            lower.startsWith("i did") || lower.startsWith("i completed") ->
                VoiceCommandResult(Intent.TOGGLE_HABIT, 0.6f,
                    entityName = trimmed.substringAfter("did").substringAfter("completed").trim())

            lower.contains("delete") || lower.contains("remove") ->
                VoiceCommandResult(Intent.DELETE_TASK, 0.5f,
                    entityName = trimmed.replace(Regex("(?i)(delete|remove|task)"), "").trim())

            else -> VoiceCommandResult(Intent.UNKNOWN, 0f)
        }
    }

    /**
     * Batch-parse multiple commands from a single voice input.
     * Splits on "and", "then", "also" etc.
     */
    fun parseBatch(text: String): List<VoiceCommandResult> {
        val segments = text.split(Regex("""\s+(?:and|then|also|plus)\s+""", RegexOption.IGNORE_CASE))
        return segments.map { parse(it) }
    }

    /**
     * Two-tier parse: tries regex first, falls back to LLM if confidence < threshold.
     * Use this for production voice processing.
     */
    suspend fun parseWithLlmFallback(
        text: String,
        llmProcessor: com.mindsetpro.ml.LlmVoiceProcessor?,
        confidenceThreshold: Float = 0.7f
    ): VoiceCommandResult {
        val regexResult = parse(text)
        if (regexResult.confidence >= confidenceThreshold || llmProcessor == null) {
            return regexResult
        }
        // Tier 2: LLM fallback
        return llmProcessor.classify(text) ?: regexResult
    }
}
