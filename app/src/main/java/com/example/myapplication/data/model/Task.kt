package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString().take(8),
    val task: String,
    val category: String = Category.WORK.label,
    val priority: String = Priority.MEDIUM.label,
    val isCompleted: Boolean = false,
    val date: String? = LocalDate.now().toString(),
    val createdAt: String = LocalDateTime.now().toString(),
    val dueTime: String? = null // ISO datetime string
) {
    val done: Boolean get() = isCompleted

    fun copy(
        id: String = this.id,
        task: String = this.task,
        category: String = this.category,
        priority: String = this.priority,
        isCompleted: Boolean = this.isCompleted,
        done: Boolean = this.isCompleted,
        date: String? = this.date,
        createdAt: String = this.createdAt,
        dueTime: String? = this.dueTime
    ): Task = Task(
        id = id,
        task = task,
        category = category,
        priority = priority,
        isCompleted = if (done != this.isCompleted) done else isCompleted,
        date = date,
        createdAt = createdAt,
        dueTime = dueTime
    )
}
