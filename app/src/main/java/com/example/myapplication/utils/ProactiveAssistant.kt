package com.example.myapplication.utils

import android.content.Context
import android.util.Log
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.HabitRepository
import com.example.myapplication.data.repository.TaskRepository
import com.example.myapplication.data.repository.BedtimeRepository
import com.example.myapplication.ml.AnalyticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * ProactiveAssistant — Monitors app state and generates smart voice suggestions.
 * Used by the Vérité voice agent to proactively help users when they miss things.
 */
class ProactiveAssistant(context: Context) {

    companion object {
        private const val TAG = "ProactiveAssistant"
    }

    private val db = AppDatabase.getDatabase(context)
    private val taskRepo = TaskRepository(db.taskDao())
    private val habitRepo = HabitRepository(db.habitDao(), db.habitCompletionDao())
    private val bedtimeRepo = BedtimeRepository(db.bedtimeItemDao())
    private val analytics = AnalyticsEngine(habitRepo, taskRepo)

    data class Suggestion(
        val message: String,
        val priority: SuggestionPriority,
        val action: String? = null  // intent name to execute if user says "yes"
    )

    enum class SuggestionPriority { LOW, MEDIUM, HIGH, URGENT }

    /**
     * Get all relevant suggestions for the current moment.
     */
    suspend fun getSuggestions(): List<Suggestion> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<Suggestion>()
        val now = LocalTime.now()
        val hour = now.hour

        try {
            val snapshot = analytics.todaySnapshot()
            val momentum = analytics.computeMomentumScore()

            // ── Time-based suggestions ──
            when {
                hour in 6..9 -> suggestions.addAll(morningChecks(snapshot, momentum))
                hour in 10..14 -> suggestions.addAll(middayChecks(snapshot))
                hour in 15..19 -> suggestions.addAll(afternoonChecks(snapshot, momentum))
                hour in 20..23 -> suggestions.addAll(eveningChecks(snapshot))
            }

            // ── Always-on checks ──
            suggestions.addAll(urgentChecks(snapshot))
            suggestions.addAll(streakProtection())
            suggestions.addAll(momentumFeedback(momentum))

        } catch (e: Exception) {
            Log.e(TAG, "Error generating suggestions", e)
        }

        // Sort by priority (urgent first) and limit to top 3
        suggestions.sortedByDescending { it.priority.ordinal }.take(3)
    }

    /**
     * Get a greeting appropriate for the current time of day.
     */
    suspend fun getGreeting(userName: String?): String {
        val hour = LocalTime.now().hour
        val name = userName?.let { ", $it" } ?: ""
        val snapshot = try { analytics.todaySnapshot() } catch (_: Exception) { null }

        return when {
            hour in 5..11 -> {
                val taskInfo = snapshot?.let {
                    if (it.tasksTotal > 0) " You have ${it.tasksTotal} tasks lined up today." else ""
                } ?: ""
                "Good morning$name!$taskInfo Ready to make today count?"
            }
            hour in 12..16 -> {
                val progress = snapshot?.let {
                    if (it.habitsDone > 0) " You've already completed ${it.habitsDone} habits — nice work!" else ""
                } ?: ""
                "Good afternoon$name!$progress"
            }
            hour in 17..20 -> {
                "Good evening$name! Let's wind down and check on your progress."
            }
            else -> {
                "Hey$name! Ready for a restful night?"
            }
        }
    }

    /**
     * Generate a goodnight summary.
     */
    suspend fun getGoodnightMessage(userName: String?): String {
        val name = userName?.let { ", $it" } ?: ""
        return try {
            val snapshot = analytics.todaySnapshot()
            val momentum = analytics.computeMomentumScore()

            val habitLine = when {
                snapshot.habitPct >= 100f -> "You nailed all your habits today!"
                snapshot.habitPct >= 75f -> "Great job with ${snapshot.habitsDone} out of ${snapshot.habitsScheduled} habits."
                snapshot.habitPct >= 50f -> "You completed about half your habits. Tomorrow's a fresh start!"
                else -> "Tomorrow is another chance to build momentum."
            }

            val taskLine = when {
                snapshot.taskPct >= 100f -> "Every task is done — impressive."
                snapshot.pendingHigh > 0 -> "Just a heads up: ${snapshot.pendingHigh} high-priority tasks are still pending."
                else -> ""
            }

            val momentumLine = when {
                momentum.first >= 80f -> "Your momentum is ${momentum.second} — you're on fire!"
                momentum.first >= 50f -> "Momentum is building nicely."
                else -> ""
            }

            "Good night$name! $habitLine $taskLine $momentumLine Sleep well and recharge."
                .replace("  ", " ").trim()
        } catch (e: Exception) {
            "Good night$name! Rest well and see you tomorrow."
        }
    }

    // ── Morning Checks ──────────────────────────────────────────────────

    private fun morningChecks(
        snapshot: com.example.myapplication.data.model.DashboardSnapshot,
        momentum: Pair<Float, String>
    ): List<Suggestion> {
        val list = mutableListOf<Suggestion>()

        if (snapshot.tasksTotal > 0) {
            list.add(Suggestion(
                "You have ${snapshot.tasksTotal} tasks scheduled for today." +
                    if (snapshot.pendingHigh > 0) " ${snapshot.pendingHigh} are high priority." else "",
                SuggestionPriority.MEDIUM,
                "NAVIGATE_TODO"
            ))
        }

        if (snapshot.habitsScheduled > 0 && snapshot.habitsDone == 0) {
            list.add(Suggestion(
                "None of your ${snapshot.habitsScheduled} habits are done yet. Want to start checking them off?",
                SuggestionPriority.MEDIUM,
                "NAVIGATE_DASHBOARD"
            ))
        }

        return list
    }

    // ── Midday Checks ───────────────────────────────────────────────────

    private fun middayChecks(
        snapshot: com.example.myapplication.data.model.DashboardSnapshot
    ): List<Suggestion> {
        val list = mutableListOf<Suggestion>()

        if (snapshot.habitsScheduled > 0 && snapshot.habitPct < 25f) {
            list.add(Suggestion(
                "It's midday and only ${snapshot.habitsDone} of ${snapshot.habitsScheduled} habits are done. You've got this!",
                SuggestionPriority.MEDIUM,
                "NAVIGATE_DASHBOARD"
            ))
        }

        return list
    }

    // ── Afternoon Checks ────────────────────────────────────────────────

    private fun afternoonChecks(
        snapshot: com.example.myapplication.data.model.DashboardSnapshot,
        momentum: Pair<Float, String>
    ): List<Suggestion> {
        val list = mutableListOf<Suggestion>()

        val remainingHabits = snapshot.habitsScheduled - snapshot.habitsDone
        if (remainingHabits > 0) {
            list.add(Suggestion(
                "You still have $remainingHabits habit${if (remainingHabits > 1) "s" else ""} left today. Want me to list them?",
                SuggestionPriority.MEDIUM,
                "LIST_HABITS"
            ))
        }

        if (snapshot.pendingHigh > 0) {
            list.add(Suggestion(
                "Don't forget: ${snapshot.pendingHigh} high-priority task${if (snapshot.pendingHigh > 1) "s are" else " is"} still pending.",
                SuggestionPriority.HIGH,
                "LIST_TASKS"
            ))
        }

        return list
    }

    // ── Evening Checks ──────────────────────────────────────────────────

    private suspend fun eveningChecks(
        snapshot: com.example.myapplication.data.model.DashboardSnapshot
    ): List<Suggestion> {
        val list = mutableListOf<Suggestion>()

        val bedtimeItems = bedtimeRepo.getForDate()
        val unchecked = bedtimeItems.count { !it.isChecked }
        if (unchecked > 0) {
            list.add(Suggestion(
                "Time to start your bedtime routine. You have $unchecked items to check off.",
                SuggestionPriority.MEDIUM,
                "START_BEDTIME_ROUTINE"
            ))
        }

        if (snapshot.habitPct < 100f && snapshot.habitsScheduled > 0) {
            val remaining = snapshot.habitsScheduled - snapshot.habitsDone
            list.add(Suggestion(
                "Quick reminder: $remaining habit${if (remaining > 1) "s" else ""} still unchecked before bed.",
                SuggestionPriority.MEDIUM,
                "NAVIGATE_DASHBOARD"
            ))
        }

        list.add(Suggestion(
            "Would you like me to play some sleep sounds to help you wind down?",
            SuggestionPriority.LOW,
            "PLAY_SLEEP_SOUND"
        ))

        return list
    }

    // ── Urgent Checks ───────────────────────────────────────────────────

    private fun urgentChecks(
        snapshot: com.example.myapplication.data.model.DashboardSnapshot
    ): List<Suggestion> {
        val list = mutableListOf<Suggestion>()

        if (snapshot.pendingHigh >= 3) {
            list.add(Suggestion(
                "You have ${snapshot.pendingHigh} high-priority tasks piling up. Want me to help prioritize?",
                SuggestionPriority.URGENT,
                "LIST_TASKS"
            ))
        }

        return list
    }

    // ── Streak Protection ───────────────────────────────────────────────

    private suspend fun streakProtection(): List<Suggestion> {
        val list = mutableListOf<Suggestion>()
        try {
            val streaks = analytics.getAllStreakInfos()
            val atRisk = streaks.filter { it.currentStreak >= 3 && it.completionRate7d < 1.0f }

            atRisk.take(2).forEach { streak ->
                list.add(Suggestion(
                    "Your ${streak.habitName} streak is at ${streak.currentStreak} days. Don't break it today!",
                    if (streak.currentStreak >= 7) SuggestionPriority.HIGH else SuggestionPriority.MEDIUM,
                    "TOGGLE_HABIT"
                ))
            }
        } catch (_: Exception) {}
        return list
    }

    // ── Momentum Feedback ───────────────────────────────────────────────

    private fun momentumFeedback(momentum: Pair<Float, String>): List<Suggestion> {
        val list = mutableListOf<Suggestion>()

        when {
            momentum.first >= 85f -> list.add(Suggestion(
                "Your momentum is ${momentum.second} — you're absolutely crushing it! Keep going!",
                SuggestionPriority.LOW
            ))
            momentum.first < 30f -> list.add(Suggestion(
                "Your momentum needs a boost. Even completing one small habit can help build it back up.",
                SuggestionPriority.MEDIUM,
                "NAVIGATE_DASHBOARD"
            ))
        }

        return list
    }
}
