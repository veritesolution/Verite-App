package com.mindsetpro.ml

import com.mindsetpro.data.model.*
import com.mindsetpro.data.repository.HabitRepository
import com.mindsetpro.data.repository.TaskRepository

/**
 * Comprehensive analytics and KPI computation engine.
 * Powers the dashboard, notifications, and voice query responses.
 */
class AnalyticsEngine(
    private val habitRepo: HabitRepository,
    private val taskRepo: TaskRepository
) {

    // ── Today's Dashboard Snapshot ───────────────────────────────────────────

    suspend fun todaySnapshot(): DashboardSnapshot {
        val todayHabits = habitRepo.getTodayScheduled()
        val todayDone = habitRepo.getTodayCompleted()
        val totalTasks = taskRepo.totalCount()
        val doneTasks = taskRepo.doneCount()
        val pendingHigh = taskRepo.pendingHighCount()

        val allHabits = habitRepo.getAll()
        val streaks = allHabits.map { habitRepo.computeStreakInfo(it) }
        val maxStreak = streaks.maxOfOrNull { it.currentStreak } ?: 0
        val avgStreak = if (streaks.isNotEmpty())
            streaks.map { it.currentStreak }.average().toFloat() else 0f

        return DashboardSnapshot(
            habitsScheduled = todayHabits.size,
            habitsDone = todayDone.size,
            habitPct = if (todayHabits.isNotEmpty())
                (todayDone.size.toFloat() / todayHabits.size * 100) else 0f,
            tasksTotal = totalTasks,
            tasksDone = doneTasks,
            taskPct = if (totalTasks > 0)
                (doneTasks.toFloat() / totalTasks * 100) else 0f,
            pendingHigh = pendingHigh,
            currentMaxStreak = maxStreak,
            avgStreak = avgStreak
        )
    }

    // ── Streak Infos for All Habits ──────────────────────────────────────────

    suspend fun getAllStreakInfos(): List<StreakInfo> {
        return habitRepo.getAll().map { habitRepo.computeStreakInfo(it) }
    }

    // ── Category Breakdown ───────────────────────────────────────────────────

    suspend fun getCategoryBreakdown(): List<CategoryBreakdown> {
        return taskRepo.getCategoryBreakdown()
    }

    // ── Productive Momentum Score ────────────────────────────────────────────

    suspend fun computeMomentumScore(): Pair<Float, String> {
        val snapshot = todaySnapshot()
        val habitRate = snapshot.habitPct / 100f
        val taskRate = snapshot.taskPct / 100f

        val score = SentimentMoodTracker.computeMomentum(
            habitCompletionRate = habitRate,
            taskCompletionRate = taskRate,
            currentMaxStreak = snapshot.currentMaxStreak
        )
        val label = SentimentMoodTracker.momentumLabel(score)
        return score to label
    }

    // ── ML Insights ──────────────────────────────────────────────────────────

    suspend fun getHabitClusters(): List<HabitCluster> {
        val streakInfos = getAllStreakInfos()
        val weeklyBreakdowns = mutableMapOf<String, Map<Int, Float>>()
        for (habit in habitRepo.getAll()) {
            weeklyBreakdowns[habit.id] = habitRepo.getWeeklyBreakdown(habit.id)
        }
        val features = BehavioralAnalyzer.buildFeatures(streakInfos, weeklyBreakdowns)
        return BehavioralAnalyzer.clusterHabits(features)
    }

    suspend fun getPredictions(): Map<String, Float> {
        val habits = habitRepo.getAll()
        val predictions = mutableMapOf<String, Float>()
        for (habit in habits) {
            val streakInfo = habitRepo.computeStreakInfo(habit)
            val weekly = habitRepo.getWeeklyBreakdown(habit.id)
            predictions[habit.name] = BehavioralAnalyzer.predictTomorrow(streakInfo, weekly)
        }
        return predictions
    }

    // ── Day-of-Week Profile ──────────────────────────────────────────────────

    suspend fun getDayOfWeekProfile(): List<DayOfWeekProfile> {
        val heatmap = habitRepo.getHeatmapData()
        val allCompletions = mutableMapOf<String, Boolean>()
        for ((_, dayMap) in heatmap) {
            for ((date, completed) in dayMap) {
                // Merge: if any habit completed on date, mark true
                if (completed) allCompletions[date] = true
                else allCompletions.putIfAbsent(date, false)
            }
        }
        return BehavioralAnalyzer.computeDayOfWeekProfile(allCompletions, emptyMap())
    }
}
