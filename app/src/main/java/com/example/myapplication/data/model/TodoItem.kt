package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val task: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
