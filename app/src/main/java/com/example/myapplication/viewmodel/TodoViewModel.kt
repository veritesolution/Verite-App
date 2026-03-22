package com.example.myapplication.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.*
import com.example.myapplication.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class TaskFilter {
    ALL, HIGH, MEDIUM, LOW, WORK, PERSONAL
}

/**
 * ViewModel for the Todo feature, adapted from MindSetPro.
 */
class TodoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = TaskRepository(db.taskDao())

    val allTasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter

    fun setFilter(newFilter: TaskFilter) {
        _filter.value = newFilter
    }

    val filteredTasks: StateFlow<List<Task>> = combine(allTasks, _filter) { tasks, filter ->
        when (filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.HIGH -> tasks.filter { it.priority.equals("High", ignoreCase = true) }
            TaskFilter.MEDIUM -> tasks.filter { it.priority.equals("Medium", ignoreCase = true) }
            TaskFilter.LOW -> tasks.filter { it.priority.equals("Low", ignoreCase = true) }
            TaskFilter.WORK -> tasks.filter { it.category.equals("Work", ignoreCase = true) }
            TaskFilter.PERSONAL -> tasks.filter { it.category.equals("Personal", ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTasks: StateFlow<List<Task>> = repository.pendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val client = OkHttpClient()

    fun autoPrioritizeTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentPendingTasks = pendingTasks.value
                if (currentPendingTasks.isNotEmpty()) {
                    val tasksJson = currentPendingTasks.joinToString(separator = "\n") { 
                        "{ \"id\": \"${it.id}\", \"task\": \"${it.task}\", \"category\": \"${it.category}\" }" 
                    }
                    val prompt = """
                        You are an AI task prioritizer. Review the following pending tasks and assign a priority level to each (High, Medium, or Low). 
                        Respond strictly with a JSON object where the keys are the task IDs and the values are the priority string.
                        Example: {"taskId1": "High", "taskId2": "Low"}
                        Tasks:
                        $tasksJson
                    """.trimIndent()
                    
                    val requestBodyJson = JSONObject().apply {
                        put("model", "llama-3.1-8b-instant")
                        put("messages", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                        put("response_format", JSONObject().apply { put("type", "json_object") })
                    }

                    val request = Request.Builder()
                        .url("https://api.groq.com/openai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer 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")
                        .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    withContext(Dispatchers.IO) {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (body != null) {
                                    val jsonResponse = JSONObject(body)
                                    val rawContent = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                                    val startIndex = rawContent.indexOf("{")
                                    val endIndex = rawContent.lastIndexOf("}")
                                    
                                    if (startIndex != -1 && endIndex != -1 && startIndex <= endIndex) {
                                        val cleanContent = rawContent.substring(startIndex, endIndex + 1)
                                        val priorityMap = JSONObject(cleanContent)
                                        val updates = currentPendingTasks.mapNotNull { task ->
                                        if (priorityMap.has(task.id)) {
                                            val newPrio = priorityMap.getString(task.id)
                                            val normalized = newPrio.lowercase().replaceFirstChar { it.uppercase() }
                                            if (normalized in listOf("High", "Medium", "Low")) {
                                                task.copy(priority = normalized)
                                            } else null
                                        } else null
                                    }
                                    updates.forEach { updatedTask ->
                                        repository.update(updatedTask)
                                    }
                                }
                                }
                            } else {
                                Log.e("TodoViewModel", "Error fetching prioritization: ${response.code}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TodoViewModel", "Exception during auto priorization", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createTask(name: String, category: String = "Work", priority: String = "Medium") {
        viewModelScope.launch {
            repository.create(name, category, priority)
            kotlinx.coroutines.delay(500)
            autoPrioritizeTasks()
        }
    }

    fun markTaskDone(taskId: String, done: Boolean = true) {
        viewModelScope.launch {
            repository.markDone(taskId, done)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.delete(taskId)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.update(task)
        }
    }
}
