package com.example.myapplication.data.model

import androidx.room.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// ── Enums ────────────────────────────────────────────────────────────────────

enum class Priority(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

enum class Category(val label: String) {
    WORK("Work"),
    PERSONAL("Personal"),
    HEALTH("Health"),
    LEARNING("Learning"),
    OTHER("Other")
}

enum class Intent {
    ADD_TASK,
    COMPLETE_TASK,
    DELETE_TASK,
    ADD_HABIT,
    TOGGLE_HABIT,
    QUERY_STREAK,
    QUERY_TASKS,
    LIST_HABITS,
    UNKNOWN
}

// ── Habit Entity ─────────────────────────────────────────────────────────────

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val emoji: String = "🎯",
    val category: String = "Health",
    val targetDays: String = "1,2,3,4,5,6,7", // comma-separated ISO day-of-week
    val createdAt: String = LocalDate.now().toString()
)

// ── Habit Completion (history tracking) ──────────────────────────────────────

@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class HabitCompletion(
    val habitId: String,
    val date: String, // ISO date string (yyyy-MM-dd)
    val completed: Boolean = true
)

// ── Mood Entry ───────────────────────────────────────────────────────────────

@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString().take(8),
    val date: String = LocalDate.now().toString(),
    val sentimentScore: Float = 0f, // -1.0 to +1.0
    val momentumScore: Float = 0f,  // 0 to 100
    val note: String? = null,
    val createdAt: String = LocalDateTime.now().toString()
)

// ── Bedtime Routine Item ─────────────────────────────────────────────────────

@Entity(tableName = "bedtime_items")
data class BedtimeItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val orderIndex: Int,
    val isChecked: Boolean = false,
    val date: String = LocalDate.now().toString()
)

// ── Analytics Snapshot (cached KPIs) ─────────────────────────────────────────

data class DashboardSnapshot(
    val habitsScheduled: Int = 0,
    val habitsDone: Int = 0,
    val habitPct: Float = 0f,
    val tasksTotal: Int = 0,
    val tasksDone: Int = 0,
    val taskPct: Float = 0f,
    val pendingHigh: Int = 0,
    val currentMaxStreak: Int = 0,
    val avgStreak: Float = 0f
)

// ── Streak Info ──────────────────────────────────────────────────────────────

data class StreakInfo(
    val habitId: String,
    val habitName: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val completionRate7d: Float,
    val completionRate30d: Float,
    val predictedTomorrow: Float = 0f // 0.0–1.0 probability
)

// ── Voice Command Result ─────────────────────────────────────────────────────

data class VoiceCommandResult(
    val intent: Intent,
    val confidence: Float,
    val entityName: String? = null,
    val priority: String? = null,
    val category: String? = null
)

// ── Behavioral Cluster ───────────────────────────────────────────────────────

data class HabitCluster(
    val habitName: String,
    val clusterId: Int,
    val clusterLabel: String, // e.g. "Consistent Performer", "Weekend Warrior"
    val features: Map<String, Float>
)

// ── Day-of-Week Profile ──────────────────────────────────────────────────────

data class DayOfWeekProfile(
    val dayName: String,
    val dayNumber: Int,
    val avgCompletionRate: Float,
    val avgTasksCompleted: Float
)

// ── Category Breakdown ───────────────────────────────────────────────────────

data class CategoryBreakdown(
    val category: String,
    val totalItems: Int,
    val completedItems: Int,
    val completionRate: Float
)

// ── Monthly Summary (6-month trend) ──────────────────────────────────────────

data class MonthlySummary(
    val month: String,       // e.g. "Mar 2026"
    val done: Int,
    val possible: Int,
    val rate: Float           // 0–100
)

// ── Task Sentiment Breakdown ─────────────────────────────────────────────────

data class TaskSentiment(
    val taskName: String,
    val category: String,
    val priority: String,
    val sentimentScore: Float, // -1.0 to +1.0
    val valence: String        // "Positive", "Neutral", "Negative"
)

// ── Weekly Momentum History ──────────────────────────────────────────────────

data class WeeklyMomentum(
    val weekLabel: String,    // e.g. "Mar 10"
    val habitPct: Float,
    val taskPct: Float,
    val streakBonus: Float,
    val momentum: Float        // 0–100
)

// ── Task Age Distribution ────────────────────────────────────────────────────

data class TaskAge(
    val taskName: String,
    val priority: String,
    val category: String,
    val ageDays: Int
)
