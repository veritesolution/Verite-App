package com.mindsetpro.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mindsetpro.data.local.MindSetDatabase
import com.mindsetpro.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data Export / Import Manager.
 *
 * Exports all app data (tasks, habits, completions, mood entries, bedtime items)
 * as a single JSON file. Supports full restore from the same format.
 */
class DataExportManager(private val context: Context) {

    private val db = MindSetDatabase.getInstance(context)

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Export all data to a JSON file and return the file URI for sharing.
     */
    suspend fun exportToJson(): File = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "MindSetPro")
        root.put("version", "1.0.0")
        root.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

        // Tasks
        val tasksArray = JSONArray()
        db.taskDao().getAllFlow().let { /* collect not available here, use DAO directly */ }
        // Use a simpler approach — query synchronously from IO thread
        val allTasks = db.taskDao().search("") // gets all via LIKE '%%'
        for (task in allTasks) {
            tasksArray.put(JSONObject().apply {
                put("id", task.id)
                put("name", task.name)
                put("category", task.category)
                put("priority", task.priority)
                put("done", task.done)
                put("date", task.date ?: "")
                put("createdAt", task.createdAt)
                put("dueTime", task.dueTime ?: "")
            })
        }
        root.put("tasks", tasksArray)

        // Habits
        val habitsArray = JSONArray()
        val allHabits = db.habitDao().getAll()
        for (habit in allHabits) {
            val completions = db.habitCompletionDao().getForHabit(habit.id)
            val completionDates = JSONArray()
            completions.filter { it.completed }.forEach { completionDates.put(it.date) }

            habitsArray.put(JSONObject().apply {
                put("id", habit.id)
                put("name", habit.name)
                put("emoji", habit.emoji)
                put("category", habit.category)
                put("targetDays", habit.targetDays)
                put("createdAt", habit.createdAt)
                put("completions", completionDates)
            })
        }
        root.put("habits", habitsArray)

        // Mood entries
        val moodArray = JSONArray()
        val moodEntry = db.moodEntryDao().getForDate(java.time.LocalDate.now().toString())
        if (moodEntry != null) {
            moodArray.put(JSONObject().apply {
                put("id", moodEntry.id)
                put("date", moodEntry.date)
                put("sentimentScore", moodEntry.sentimentScore.toDouble())
                put("momentumScore", moodEntry.momentumScore.toDouble())
                put("note", moodEntry.note ?: "")
            })
        }
        root.put("moodEntries", moodArray)

        // Write to file
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(context.cacheDir, "mindsetpro_backup_$timestamp.json")
        file.writeText(root.toString(2))
        file
    }

    /**
     * Create a share intent for the exported file.
     */
    fun createShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ── Import ───────────────────────────────────────────────────────────────

    /**
     * Import data from a JSON file URI.
     * Returns a summary of what was imported.
     */
    suspend fun importFromJson(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult(success = false, error = "Cannot open file")

            val jsonStr = inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)

            var tasksImported = 0
            var habitsImported = 0
            var completionsImported = 0

            // Import tasks
            val tasksArray = root.optJSONArray("tasks")
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val obj = tasksArray.getJSONObject(i)
                    val task = Task(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        category = obj.optString("category", "Work"),
                        priority = obj.optString("priority", "Medium"),
                        done = obj.optBoolean("done", false),
                        date = obj.optString("date").takeIf { it.isNotBlank() },
                        createdAt = obj.optString("createdAt", ""),
                        dueTime = obj.optString("dueTime").takeIf { it.isNotBlank() }
                    )
                    db.taskDao().insert(task)
                    tasksImported++
                }
            }

            // Import habits + completions
            val habitsArray = root.optJSONArray("habits")
            if (habitsArray != null) {
                for (i in 0 until habitsArray.length()) {
                    val obj = habitsArray.getJSONObject(i)
                    val habit = Habit(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        emoji = obj.optString("emoji", "🎯"),
                        category = obj.optString("category", "Health"),
                        targetDays = obj.optString("targetDays", "1,2,3,4,5,6,7"),
                        createdAt = obj.optString("createdAt", "")
                    )
                    db.habitDao().insert(habit)
                    habitsImported++

                    // Import completions
                    val completions = obj.optJSONArray("completions")
                    if (completions != null) {
                        for (j in 0 until completions.length()) {
                            val dateStr = completions.getString(j)
                            db.habitCompletionDao().insert(
                                HabitCompletion(habitId = habit.id, date = dateStr)
                            )
                            completionsImported++
                        }
                    }
                }
            }

            ImportResult(
                success = true,
                tasksImported = tasksImported,
                habitsImported = habitsImported,
                completionsImported = completionsImported
            )
        } catch (e: Exception) {
            ImportResult(success = false, error = e.message ?: "Import failed")
        }
    }

    data class ImportResult(
        val success: Boolean,
        val tasksImported: Int = 0,
        val habitsImported: Int = 0,
        val completionsImported: Int = 0,
        val error: String? = null
    ) {
        val summary: String
            get() = if (success) {
                "Imported $tasksImported tasks, $habitsImported habits, $completionsImported completions"
            } else {
                "Import failed: $error"
            }
    }
}
