package com.mindsetpro.utils

import com.mindsetpro.data.model.*
import com.mindsetpro.data.repository.HabitRepository
import com.mindsetpro.data.repository.TaskRepository
import java.time.LocalDate
import kotlin.random.Random

/**
 * Generates realistic habit/task data with:
 *   • Weekday bias (lower weekend completion)
 *   • Gradual improvement trend over time
 *   • Random hot/cold streaks
 *   • Per-habit personality (some habits easier than others)
 *
 * Used for demo mode and UI testing.
 */
object SyntheticDataEngine {

    private val SAMPLE_HABITS = listOf(
        Triple("Morning Run", "🏃", "Health"),
        Triple("Meditate", "🧘", "Mindfulness"),
        Triple("Read 30min", "📚", "Learning"),
        Triple("Journal", "📝", "Personal"),
        Triple("Study Kotlin", "💻", "Learning"),
        Triple("Drink 2L Water", "💧", "Health"),
        Triple("No Sugar", "🚫", "Health"),
        Triple("Budget Review", "💰", "Finance"),
        Triple("Yoga", "🧘‍♀️", "Health"),
        Triple("Practice Guitar", "🎸", "Personal")
    )

    private val SAMPLE_TASKS = listOf(
        Triple("Review PR #42", "Work", "High"),
        Triple("Fix login bug", "Work", "High"),
        Triple("Write unit tests", "Work", "Medium"),
        Triple("Grocery shopping", "Personal", "Medium"),
        Triple("Call dentist", "Health", "Low"),
        Triple("Update portfolio", "Learning", "Medium"),
        Triple("Pay electricity bill", "Finance", "High"),
        Triple("Clean kitchen", "Personal", "Low"),
        Triple("Read Chapter 5", "Learning", "Medium"),
        Triple("Plan weekend trip", "Personal", "Low"),
        Triple("Deploy v2.1", "Work", "High"),
        Triple("Meal prep Sunday", "Health", "Medium"),
        Triple("Backup photos", "Personal", "Low"),
        Triple("Submit expense report", "Finance", "Medium"),
        Triple("Refactor auth module", "Work", "Medium")
    )

    /**
     * Seed the database with 90 days of synthetic habit data and sample tasks.
     */
    suspend fun seedDatabase(
        habitRepo: HabitRepository,
        taskRepo: TaskRepository,
        seed: Int = 42
    ) {
        val rng = Random(seed)

        // Create habits
        val habits = SAMPLE_HABITS.map { (name, emoji, category) ->
            habitRepo.create(
                name = name,
                emoji = emoji,
                category = category,
                targetDays = if (rng.nextFloat() > 0.3f)
                    listOf(1, 2, 3, 4, 5, 6, 7)
                else
                    listOf(1, 2, 3, 4, 5) // weekdays only
            )
        }

        // Generate 90 days of history
        val today = LocalDate.now()
        for (habit in habits) {
            val baseProbability = seededProb(habit.id, rng)
            var d = today.minusDays(90)

            while (!d.isAfter(today)) {
                val dow = d.dayOfWeek.value
                val targetDays = habit.targetDays.split(",").map { it.trim().toInt() }

                if (dow in targetDays) {
                    // Weekday bias
                    val weekendPenalty = if (dow >= 6) -0.12f else 0f
                    // Gradual improvement
                    val daysAgo = today.toEpochDay() - d.toEpochDay()
                    val trendBoost = (90 - daysAgo) / 900f
                    // Random hot/cold streaks
                    val streakNoise = (rng.nextFloat() - 0.5f) * 0.2f

                    val prob = (baseProbability + weekendPenalty + trendBoost + streakNoise)
                        .coerceIn(0.1f, 0.95f)

                    if (rng.nextFloat() < prob) {
                        habitRepo.toggle(habit.id, d.toString())
                    }
                }
                d = d.plusDays(1)
            }
        }

        // Create tasks (some done, some pending)
        SAMPLE_TASKS.forEachIndexed { index, (name, category, priority) ->
            val task = taskRepo.create(
                name = name,
                category = category,
                priority = priority,
                dateStr = today.minusDays(rng.nextLong(0, 7)).toString()
            )
            // Mark ~60% as done
            if (rng.nextFloat() < 0.6f) {
                taskRepo.markDone(task.id, true)
            }
        }
    }

    private fun seededProb(habitId: String, rng: Random): Float {
        // Deterministic per-habit base probability
        val hash = habitId.hashCode().toUInt()
        return 0.55f + (hash % 30u).toFloat() / 100f // 0.55–0.85
    }
}
