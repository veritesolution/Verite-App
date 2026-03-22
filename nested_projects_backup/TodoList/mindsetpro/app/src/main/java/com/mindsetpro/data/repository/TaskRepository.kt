package com.mindsetpro.data.repository

import com.mindsetpro.data.local.TaskDao
import com.mindsetpro.data.model.Task
import com.mindsetpro.data.model.CategoryBreakdown
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TaskRepository(private val dao: TaskDao) {

    // ── CRUD ─────────────────────────────────────────────────────────────────

    val allTasks: Flow<List<Task>> = dao.getAllFlow()
    val pendingTasks: Flow<List<Task>> = dao.getPendingFlow()

    suspend fun create(
        name: String,
        category: String = "Work",
        priority: String = "Medium",
        dateStr: String? = null,
        dueTime: String? = null
    ): Task {
        val task = Task(
            name = name,
            category = category,
            priority = priority,
            date = dateStr ?: LocalDate.now().toString(),
            dueTime = dueTime
        )
        dao.insert(task)
        return task
    }

    suspend fun getById(id: String): Task? = dao.getById(id)

    suspend fun update(task: Task) = dao.update(task)

    suspend fun delete(taskId: String) = dao.deleteById(taskId)

    suspend fun markDone(taskId: String, done: Boolean = true) = dao.markDone(taskId, done)

    suspend fun search(query: String): List<Task> = dao.search(query)

    fun getByCategory(category: String): Flow<List<Task>> = dao.getByCategory(category)

    fun getByPriority(priority: String): Flow<List<Task>> = dao.getByPriority(priority)

    // ── Analytics ────────────────────────────────────────────────────────────

    suspend fun totalCount(): Int = dao.totalCount()
    suspend fun doneCount(): Int = dao.doneCount()
    suspend fun pendingHighCount(): Int = dao.pendingHighCount()

    suspend fun getCategoryBreakdown(): List<CategoryBreakdown> {
        return dao.getCategoryStats().map { stat ->
            CategoryBreakdown(
                category = stat.category,
                totalItems = stat.total,
                completedItems = stat.completed,
                completionRate = if (stat.total > 0) stat.completed.toFloat() / stat.total else 0f
            )
        }
    }
}
