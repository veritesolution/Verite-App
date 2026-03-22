package com.example.myapplication.data.local

import androidx.room.*
import com.example.myapplication.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(item: Task)

    @Update
    suspend fun updateTask(item: Task)

    @Delete
    suspend fun deleteTask(item: Task)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: String)

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getById(taskId: String): Task?

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY " +
           "CASE priority WHEN 'High' THEN 0 WHEN 'Medium' THEN 1 ELSE 2 END")
    fun getPendingFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE category = :category")
    fun getByCategory(category: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE priority = :priority AND isCompleted = 0")
    fun getByPriority(priority: String): Flow<List<Task>>

    @Query("UPDATE tasks SET isCompleted = :done WHERE id = :taskId")
    suspend fun markDone(taskId: String, done: Boolean)

    @Query("SELECT * FROM tasks WHERE task LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<Task>

    // Analytics queries
    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1")
    suspend fun doneCount(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE priority = 'High' AND isCompleted = 0")
    suspend fun pendingHighCount(): Int

    @Query("SELECT category, COUNT(*) as total, " +
           "SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) as completed " +
           "FROM tasks GROUP BY category")
    suspend fun getCategoryStats(): List<TaskCategoryStat>

    @Query("SELECT priority, COUNT(*) as total, " +
           "SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) as completed " +
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
