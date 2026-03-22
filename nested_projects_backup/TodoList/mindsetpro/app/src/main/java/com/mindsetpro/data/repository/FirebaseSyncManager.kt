package com.mindsetpro.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.mindsetpro.data.model.Task
import com.mindsetpro.data.model.Habit
import com.mindsetpro.data.model.HabitCompletion
import kotlinx.coroutines.tasks.await

/**
 * Optional Firebase Firestore cloud sync.
 * Mirrors local Room data to Firestore for cross-device sync & backup.
 *
 * Setup: Add google-services.json to app/ and enable Firestore in Firebase Console.
 */
class FirebaseSyncManager(
    private val userId: String = "default_user"
) {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val userRef by lazy { db.collection("users").document(userId) }

    var isEnabled: Boolean = false
        private set

    fun initialize(): Boolean {
        return try {
            db.hashCode() // trigger lazy init
            isEnabled = true
            true
        } catch (e: Exception) {
            isEnabled = false
            false
        }
    }

    // ── Tasks ────────────────────────────────────────────────────────────────

    suspend fun syncTasks(tasks: List<Task>) {
        if (!isEnabled) return
        try {
            val batch = db.batch()
            val tasksRef = userRef.collection("tasks")
            for (task in tasks) {
                val doc = tasksRef.document(task.id)
                batch.set(doc, mapOf(
                    "id" to task.id,
                    "name" to task.name,
                    "category" to task.category,
                    "priority" to task.priority,
                    "done" to task.done,
                    "date" to task.date,
                    "createdAt" to task.createdAt,
                    "dueTime" to task.dueTime
                ))
            }
            batch.commit().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadTasks(): List<Map<String, Any>> {
        if (!isEnabled) return emptyList()
        return try {
            val snapshot = userRef.collection("tasks").get().await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Habits ───────────────────────────────────────────────────────────────

    suspend fun syncHabits(habits: List<Habit>) {
        if (!isEnabled) return
        try {
            val batch = db.batch()
            val habitsRef = userRef.collection("habits")
            for (habit in habits) {
                val doc = habitsRef.document(habit.id)
                batch.set(doc, mapOf(
                    "id" to habit.id,
                    "name" to habit.name,
                    "emoji" to habit.emoji,
                    "category" to habit.category,
                    "targetDays" to habit.targetDays,
                    "createdAt" to habit.createdAt
                ))
            }
            batch.commit().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncCompletions(completions: List<HabitCompletion>) {
        if (!isEnabled) return
        try {
            val batch = db.batch()
            val compRef = userRef.collection("completions")
            for (c in completions) {
                val doc = compRef.document("${c.habitId}_${c.date}")
                batch.set(doc, mapOf(
                    "habitId" to c.habitId,
                    "date" to c.date,
                    "completed" to c.completed
                ))
            }
            batch.commit().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Full Backup / Restore ────────────────────────────────────────────────

    suspend fun backupAll(tasks: List<Task>, habits: List<Habit>, completions: List<HabitCompletion>) {
        syncTasks(tasks)
        syncHabits(habits)
        syncCompletions(completions)
    }
}
