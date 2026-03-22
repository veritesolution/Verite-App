package com.mindsetpro.data.local

import androidx.room.*
import com.mindsetpro.data.model.*
import kotlinx.coroutines.flow.Flow

// ── Task DAO ─────────────────────────────────────────────────────────────────

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: String)

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getById(taskId: String): Task?

    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY " +
           "CASE priority WHEN 'High' THEN 0 WHEN 'Medium' THEN 1 ELSE 2 END")
    fun getPendingFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE category = :category")
    fun getByCategory(category: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE priority = :priority AND done = 0")
    fun getByPriority(priority: String): Flow<List<Task>>

    @Query("UPDATE tasks SET done = :done WHERE id = :taskId")
    suspend fun markDone(taskId: String, done: Boolean)

    @Query("SELECT * FROM tasks WHERE name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<Task>

    // Analytics queries
    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE done = 1")
    suspend fun doneCount(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE priority = 'High' AND done = 0")
    suspend fun pendingHighCount(): Int

    @Query("SELECT category, COUNT(*) as total, " +
           "SUM(CASE WHEN done = 1 THEN 1 ELSE 0 END) as completed " +
           "FROM tasks GROUP BY category")
    suspend fun getCategoryStats(): List<TaskCategoryStat>

    @Query("SELECT priority, COUNT(*) as total, " +
           "SUM(CASE WHEN done = 1 THEN 1 ELSE 0 END) as completed " +
           "FROM tasks GROUP BY priority")
    suspend fun getPriorityStats(): List<TaskPriorityStat>
}

data class TaskCategoryStat(
    val category: String,
    val total: Int,
    val completed: Int
)

data class TaskPriorityStat(
    val priority: String,
    val total: Int,
    val completed: Int
)

// ── Habit DAO ────────────────────────────────────────────────────────────────

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: Habit)

    @Update
    suspend fun update(habit: Habit)

    @Delete
    suspend fun delete(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteById(habitId: String)

    @Query("SELECT * FROM habits ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Habit>>

    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<Habit>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getById(habitId: String): Habit?

    @Query("SELECT * FROM habits WHERE name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<Habit>

    @Query("SELECT * FROM habits WHERE category = :category")
    fun getByCategory(category: String): Flow<List<Habit>>
}

// ── Habit Completion DAO ─────────────────────────────────────────────────────

@Dao
interface HabitCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: HabitCompletion)

    @Delete
    suspend fun delete(completion: HabitCompletion)

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId ORDER BY date DESC")
    fun getForHabitFlow(habitId: String): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId ORDER BY date DESC")
    suspend fun getForHabit(habitId: String): List<HabitCompletion>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun getEntry(habitId: String, date: String): HabitCompletion?

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun deleteEntry(habitId: String, date: String)

    @Query("SELECT * FROM habit_completions WHERE date = :date")
    suspend fun getForDate(date: String): List<HabitCompletion>

    @Query("SELECT * FROM habit_completions WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getInRange(startDate: String, endDate: String): List<HabitCompletion>

    @Query("SELECT COUNT(*) FROM habit_completions WHERE habitId = :habitId AND completed = 1")
    suspend fun totalCompletions(habitId: String): Int

    @Query("SELECT COUNT(*) FROM habit_completions WHERE habitId = :habitId " +
           "AND completed = 1 AND date BETWEEN :startDate AND :endDate")
    suspend fun completionsInRange(habitId: String, startDate: String, endDate: String): Int
}

// ── Mood Entry DAO ───────────────────────────────────────────────────────────

@Dao
interface MoodEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MoodEntry)

    @Query("SELECT * FROM mood_entries ORDER BY date DESC")
    fun getAllFlow(): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entries WHERE date = :date")
    suspend fun getForDate(date: String): MoodEntry?

    @Query("SELECT * FROM mood_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getInRange(startDate: String, endDate: String): List<MoodEntry>
}

// ── Bedtime Item DAO ─────────────────────────────────────────────────────────

@Dao
interface BedtimeItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BedtimeItem)

    @Update
    suspend fun update(item: BedtimeItem)

    @Query("SELECT * FROM bedtime_items WHERE date = :date ORDER BY orderIndex")
    fun getForDateFlow(date: String): Flow<List<BedtimeItem>>

    @Query("SELECT * FROM bedtime_items WHERE date = :date ORDER BY orderIndex")
    suspend fun getForDate(date: String): List<BedtimeItem>

    @Query("UPDATE bedtime_items SET isChecked = :checked WHERE id = :itemId")
    suspend fun setChecked(itemId: String, checked: Boolean)

    @Query("DELETE FROM bedtime_items WHERE date = :date")
    suspend fun clearForDate(date: String)
}
