package com.example.myapplication.data.local

import androidx.room.*
import com.example.myapplication.data.model.*
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM bedtime_items WHERE id = :itemId")
    suspend fun deleteById(itemId: String)
}
