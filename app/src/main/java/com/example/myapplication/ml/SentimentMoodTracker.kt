package com.example.myapplication.ml

/**
 * Lexicon-based sentiment engine for habit & task text.
 * Also computes 'productive momentum' — a composite score.
 */
object SentimentMoodTracker {

    private val POS_WORDS = setOf(
        "exercise", "run", "meditate", "read", "learn", "grow", "achieve", "build",
        "create", "improve", "study", "practice", "healthy", "focus", "goal",
        "complete", "success", "accomplish", "progress", "advance", "skill",
        "journal", "gratitude", "review", "plan", "invest", "save"
    )

    private val NEG_WORDS = setOf(
        "cancel", "delete", "remove", "miss", "fail", "skip", "late", "overdue",
        "stress", "anxious", "tired", "lazy", "procrastinate", "avoid", "forget",
        "rush", "urgent", "crisis", "fix", "bug", "problem", "issue", "error"
    )

    fun analyzeText(text: String): Float {
        val words = text.lowercase().split(Regex("""\W+""")).toSet()
        val pos = words.count { it in POS_WORDS }
        val neg = words.count { it in NEG_WORDS }
        val total = pos + neg
        if (total == 0) return 0f
        return ((pos - neg).toFloat() / total).coerceIn(-1f, 1f)
    }

    fun computeMomentum(
        habitCompletionRate: Float,
        taskCompletionRate: Float,
        currentMaxStreak: Int,
        avgSentiment: Float = 0f
    ): Float {
        val wHabit = 0.40f
        val wTask = 0.30f
        val wStreak = 0.20f
        val wSentiment = 0.10f

        val normalizedStreak = (currentMaxStreak.coerceAtMost(30) / 30f)
        val normalizedSentiment = (avgSentiment + 1f) / 2f

        val score = (
            wHabit * habitCompletionRate +
            wTask * taskCompletionRate +
            wStreak * normalizedStreak +
            wSentiment * normalizedSentiment
        ) * 100f

        return score.coerceIn(0f, 100f)
    }

    fun momentumLabel(score: Float): String = when {
        score >= 85f -> "On Fire!"
        score >= 70f -> "Strong Momentum"
        score >= 50f -> "Building Up"
        score >= 30f -> "Getting Started"
        else         -> "Needs a Boost"
    }

    fun batchAnalyze(texts: List<String>): Float {
        if (texts.isEmpty()) return 0f
        return texts.map { analyzeText(it) }.average().toFloat()
    }

    /**
     * Per-task sentiment score with Positive/Neutral/Negative valence (Phase 8).
     */
    fun taskSentimentBreakdown(tasks: List<com.example.myapplication.data.model.Task>): List<com.example.myapplication.data.model.TaskSentiment> {
        return tasks.map { t ->
            val score = analyzeText(t.task)
            com.example.myapplication.data.model.TaskSentiment(
                taskName = t.task,
                category = t.category,
                priority = t.priority,
                sentimentScore = score,
                valence = when {
                    score > 0f -> "Positive"
                    score < 0f -> "Negative"
                    else       -> "Neutral"
                }
            )
        }.sortedByDescending { it.sentimentScore }
    }
}
