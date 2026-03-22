package com.mindsetpro.ml

/**
 * Lexicon-based sentiment engine for habit & task text.
 * Also computes 'productive momentum' — a composite of
 * habit_completion + task_completion + streak_length weighted sum.
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

    /**
     * Returns sentiment score from -1.0 (very negative) to +1.0 (very positive).
     */
    fun analyzeText(text: String): Float {
        val words = text.lowercase().split(Regex("""\W+""")).toSet()
        val pos = words.count { it in POS_WORDS }
        val neg = words.count { it in NEG_WORDS }
        val total = pos + neg
        if (total == 0) return 0f
        return ((pos - neg).toFloat() / total).coerceIn(-1f, 1f)
    }

    /**
     * Productive momentum: composite score 0–100.
     *
     * @param habitCompletionRate  0.0–1.0 (today's habit %)
     * @param taskCompletionRate   0.0–1.0 (today's task %)
     * @param currentMaxStreak     current longest streak count
     * @param avgSentiment         avg text sentiment across today's items
     */
    fun computeMomentum(
        habitCompletionRate: Float,
        taskCompletionRate: Float,
        currentMaxStreak: Int,
        avgSentiment: Float = 0f
    ): Float {
        // Weights
        val wHabit = 0.40f
        val wTask = 0.30f
        val wStreak = 0.20f
        val wSentiment = 0.10f

        // Normalize streak (cap at 30 for scoring)
        val normalizedStreak = (currentMaxStreak.coerceAtMost(30) / 30f)
        // Shift sentiment from [-1,1] to [0,1]
        val normalizedSentiment = (avgSentiment + 1f) / 2f

        val score = (
            wHabit * habitCompletionRate +
            wTask * taskCompletionRate +
            wStreak * normalizedStreak +
            wSentiment * normalizedSentiment
        ) * 100f

        return score.coerceIn(0f, 100f)
    }

    /**
     * Classify momentum into user-friendly labels.
     */
    fun momentumLabel(score: Float): String = when {
        score >= 85f -> "🔥 On Fire!"
        score >= 70f -> "💪 Strong Momentum"
        score >= 50f -> "📈 Building Up"
        score >= 30f -> "🌱 Getting Started"
        else         -> "😴 Needs a Boost"
    }

    /**
     * Analyze a list of task/habit names and return average sentiment.
     */
    fun batchAnalyze(texts: List<String>): Float {
        if (texts.isEmpty()) return 0f
        return texts.map { analyzeText(it) }.average().toFloat()
    }
}
