package com.example.myapplication.data.repository

import com.example.myapplication.data.local.HabitDao
import com.example.myapplication.data.local.HabitCompletionDao
import com.example.myapplication.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class HabitRepository(
    private val habitDao: HabitDao,
    private val completionDao: HabitCompletionDao
) {

    // ── CRUD ─────────────────────────────────────────────────────────────────

    val allHabits: Flow<List<Habit>> = habitDao.getAllFlow()

    suspend fun create(
        name: String,
        emoji: String = "🎯",
        category: String = "Health",
        targetDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7)
    ): Habit {
        val habit = Habit(
            name = name,
            emoji = emoji,
            category = category,
            targetDays = targetDays.joinToString(",")
        )
        habitDao.insert(habit)
        return habit
    }

    suspend fun getById(id: String): Habit? = habitDao.getById(id)
    suspend fun getAll(): List<Habit> = habitDao.getAll()
    suspend fun update(habit: Habit) = habitDao.update(habit)
    suspend fun delete(habitId: String) = habitDao.deleteById(habitId)
    suspend fun search(query: String): List<Habit> = habitDao.search(query)

    // ── Toggle Completion ────────────────────────────────────────────────────

    suspend fun toggle(habitId: String, date: String = LocalDate.now().toString()) {
        val existing = completionDao.getEntry(habitId, date)
        if (existing != null) {
            completionDao.deleteEntry(habitId, date)
        } else {
            completionDao.insert(HabitCompletion(habitId = habitId, date = date))
        }
    }

    suspend fun isCompletedToday(habitId: String): Boolean {
        return completionDao.getEntry(habitId, LocalDate.now().toString()) != null
    }

    suspend fun getCompletionsForDate(habitId: String, date: String): Boolean {
        return completionDao.getEntry(habitId, date) != null
    }

    fun getCompletionsFlow(habitId: String): Flow<List<HabitCompletion>> =
        completionDao.getForHabitFlow(habitId)

    // ── Streak Computation ───────────────────────────────────────────────────

    suspend fun computeStreakInfo(habit: Habit): StreakInfo {
        val completions = completionDao.getForHabit(habit.id)
        val completedDates = completions
            .filter { it.completed }
            .map { LocalDate.parse(it.date) }
            .toSet()

        val targetDaysList = habit.targetDays.split(",").map { it.trim().toInt() }

        // Current streak
        var currentStreak = 0
        var d = LocalDate.now()
        while (true) {
            if (d.dayOfWeek.value in targetDaysList) {
                if (d in completedDates) {
                    currentStreak++
                } else if (d != LocalDate.now()) {
                    // if not today and not completed, streak is broken
                    break
                }
            }
            d = d.minusDays(1)
            if (d.isBefore(LocalDate.now().minusDays(365))) break
        }

        // Longest streak
        var longestStreak = 0
        var tempStreak = 0
        d = LocalDate.now().minusDays(365)
        while (!d.isAfter(LocalDate.now())) {
            if (d.dayOfWeek.value in targetDaysList) {
                if (d in completedDates) {
                    tempStreak++
                    longestStreak = maxOf(longestStreak, tempStreak)
                } else {
                    tempStreak = 0
                }
            }
            d = d.plusDays(1)
        }

        // Completion rates
        val rate7d = computeCompletionRate(habit.id, targetDaysList, 7)
        val rate30d = computeCompletionRate(habit.id, targetDaysList, 30)

        return StreakInfo(
            habitId = habit.id,
            habitName = habit.name,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            completionRate7d = rate7d,
            completionRate30d = rate30d
        )
    }

    private suspend fun computeCompletionRate(
        habitId: String,
        targetDays: List<Int>,
        windowDays: Int
    ): Float {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(windowDays.toLong())
        val completions = completionDao.completionsInRange(
            habitId, startDate.toString(), endDate.toString()
        )
        var possible = 0
        var d = startDate
        while (!d.isAfter(endDate)) {
            if (d.dayOfWeek.value in targetDays) possible++
            d = d.plusDays(1)
        }
        return if (possible > 0) completions.toFloat() / possible else 0f
    }

    // ── Weekly / Monthly Breakdown ───────────────────────────────────────────

    suspend fun getWeeklyBreakdown(habitId: String): Map<Int, Float> {
        val completions = completionDao.getForHabit(habitId)
        val completedDates = completions.map { LocalDate.parse(it.date) }
        val countByDay = IntArray(7)
        val totalByDay = IntArray(7)

        val start = LocalDate.now().minusDays(90)
        var d = start
        while (!d.isAfter(LocalDate.now())) {
            val dow = d.dayOfWeek.value // 1=Monday..7=Sunday
            totalByDay[dow - 1]++
            if (d in completedDates) countByDay[dow - 1]++
            d = d.plusDays(1)
        }

        return (1..7).associate { day ->
            day to if (totalByDay[day - 1] > 0)
                countByDay[day - 1].toFloat() / totalByDay[day - 1]
            else 0f
        }
    }

    // ── Heatmap Data (last 90 days) ──────────────────────────────────────────

    suspend fun getHeatmapData(): Map<String, Map<String, Boolean>> {
        val habits = habitDao.getAll()
        val result = mutableMapOf<String, Map<String, Boolean>>()
        val startDate = LocalDate.now().minusDays(90)
        val endDate = LocalDate.now()

        for (habit in habits) {
            val completions = completionDao.getInRange(startDate.toString(), endDate.toString())
                .filter { it.habitId == habit.id && it.completed }
                .map { it.date }
                .toSet()

            val dayMap = mutableMapOf<String, Boolean>()
            var d = startDate
            while (!d.isAfter(endDate)) {
                dayMap[d.toString()] = d.toString() in completions
                d = d.plusDays(1)
            }
            result[habit.name] = dayMap
        }
        return result
    }

    // ── Today's Scheduled Habits ─────────────────────────────────────────────

    suspend fun getTodayScheduled(): List<Habit> {
        val todayDow = LocalDate.now().dayOfWeek.value
        return habitDao.getAll().filter { habit ->
            todayDow in habit.targetDays.split(",").map { it.trim().toInt() }
        }
    }

    suspend fun getTodayCompleted(): List<Habit> {
        val today = LocalDate.now().toString()
        val completions = completionDao.getForDate(today)
        val completedIds = completions.filter { it.completed }.map { it.habitId }.toSet()
        return habitDao.getAll().filter { it.id in completedIds }
    }
}
