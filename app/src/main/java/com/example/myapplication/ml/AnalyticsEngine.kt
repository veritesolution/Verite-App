package com.example.myapplication.ml

import com.example.myapplication.data.model.*
import com.example.myapplication.data.repository.HabitRepository
import com.example.myapplication.data.repository.TaskRepository

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

    // ── Monthly Summary (Phase 9) ───────────────────────────────────────────

    suspend fun getMonthlySummary(months: Int = 6): List<MonthlySummary> {
        val today = java.time.LocalDate.now()
        val habits = habitRepo.getAll()
        val results = mutableListOf<MonthlySummary>()

        for (mOffset in months - 1 downTo 0) {
            val monthDate = today.minusMonths(mOffset.toLong())
            val monthStart = monthDate.withDayOfMonth(1)
            val monthEnd = monthDate.withDayOfMonth(monthDate.lengthOfMonth())
            val label = monthStart.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))

            var done = 0
            var possible = 0
            var cur = monthStart
            while (cur <= monthEnd && cur <= today) {
                val dow = cur.dayOfWeek.value
                for (h in habits) {
                    val targetDays = h.targetDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (dow in targetDays) {
                        possible++
                        val completions = habitRepo.getCompletionsForDate(h.id, cur.toString())
                        if (completions) done++
                    }
                }
                cur = cur.plusDays(1)
            }

            results.add(MonthlySummary(
                month = label,
                done = done,
                possible = possible,
                rate = if (possible > 0) (done.toFloat() / possible * 100) else 0f
            ))
        }
        return results
    }

    // ── 30-Day Habit Time Series (Phase 9) ──────────────────────────────────

    suspend fun getHabit30dTimeSeries(): List<Pair<String, Float>> {
        val today = java.time.LocalDate.now()
        val habits = habitRepo.getAll()
        val results = mutableListOf<Pair<String, Float>>()

        for (i in 29 downTo 0) {
            val d = today.minusDays(i.toLong())
            val dow = d.dayOfWeek.value
            val scheduled = habits.filter { h ->
                val targetDays = h.targetDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                dow in targetDays
            }
            if (scheduled.isEmpty()) continue

            var done = 0
            for (h in scheduled) {
                val completed = habitRepo.getCompletionsForDate(h.id, d.toString())
                if (completed) done++
            }
            val pct = (done.toFloat() / scheduled.size * 100)
            results.add(d.toString() to pct)
        }
        return results
    }

    // ── Task Age Distribution (Phase 9) ─────────────────────────────────────

    suspend fun getTaskAgeDistribution(): List<TaskAge> {
        val today = java.time.LocalDate.now()
        val pendingTasks = taskRepo.search("").filter { !it.done }
        return pendingTasks.mapNotNull { t ->
            val taskDate = try { java.time.LocalDate.parse(t.date) } catch (_: Exception) { null }
            if (taskDate != null) {
                val ageDays = java.time.temporal.ChronoUnit.DAYS.between(taskDate, today).toInt()
                TaskAge(
                    taskName = t.task,
                    priority = t.priority,
                    category = t.category,
                    ageDays = ageDays.coerceAtLeast(0)
                )
            } else null
        }.sortedByDescending { it.ageDays }
    }

    // ── Weekly Momentum History (Phase 8) ───────────────────────────────────

    suspend fun getWeeklyMomentumHistory(weeks: Int = 12): List<WeeklyMomentum> {
        val today = java.time.LocalDate.now()
        val habits = habitRepo.getAll()
        val results = mutableListOf<WeeklyMomentum>()

        for (w in weeks - 1 downTo 0) {
            val weekStart = today.minusWeeks(w.toLong()).with(java.time.DayOfWeek.MONDAY)
            val weekEnd = weekStart.plusDays(6)
            val label = weekStart.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd"))

            var hDone = 0
            var hPossible = 0
            var cur = weekStart
            while (cur <= weekEnd && cur <= today) {
                val dow = cur.dayOfWeek.value
                for (h in habits) {
                    val targetDays = h.targetDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (dow in targetDays) {
                        hPossible++
                        val completed = habitRepo.getCompletionsForDate(h.id, cur.toString())
                        if (completed) hDone++
                    }
                }
                cur = cur.plusDays(1)
            }

            val hPct = if (hPossible > 0) hDone.toFloat() / hPossible * 100 else 0f
            val tPct = try {
                val total = taskRepo.totalCount()
                val done = taskRepo.doneCount()
                if (total > 0) done.toFloat() / total * 100 else 0f
            } catch (_: Exception) { 0f }

            val avgStreak = if (habits.isNotEmpty()) {
                habits.map { habitRepo.computeStreakInfo(it).currentStreak }.average().toFloat()
            } else 0f
            val streakBonus = (avgStreak.coerceAtMost(21f) / 21f * 100f)

            val momentum = (0.5f * hPct + 0.3f * tPct + 0.2f * streakBonus).coerceIn(0f, 100f)

            results.add(WeeklyMomentum(
                weekLabel = label,
                habitPct = hPct,
                taskPct = tPct,
                streakBonus = streakBonus,
                momentum = momentum
            ))
        }
        return results
    }
}
