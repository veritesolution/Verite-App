package com.example.myapplication.data.local

import androidx.room.*
import com.example.myapplication.data.model.TodoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items ORDER BY timestamp DESC")
    fun getAllTasks(): Flow<List<TodoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(item: TodoItem)

    @Update
    suspend fun updateTask(item: TodoItem)

    @Delete
    suspend fun deleteTask(item: TodoItem)
}
