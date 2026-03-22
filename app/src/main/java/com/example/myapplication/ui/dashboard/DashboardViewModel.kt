package com.example.myapplication.ui.dashboard

import android.app.Application
import androidx.lifecycle.*
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.*
import com.example.myapplication.data.repository.HabitRepository
import com.example.myapplication.data.repository.TaskRepository
import com.example.myapplication.data.repository.BedtimeRepository
import com.example.myapplication.ml.AnalyticsEngine
import com.example.myapplication.ml.SentimentMoodTracker
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.myapplication.utils.TaskClassifier
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Central ViewModel powering the MindSet Pro dashboard.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val taskRepo = TaskRepository(db.taskDao())
    private val habitRepo = HabitRepository(db.habitDao(), db.habitCompletionDao())
    private val bedtimeRepo = BedtimeRepository(db.bedtimeItemDao())
    val analytics = AnalyticsEngine(habitRepo, taskRepo)

    // ── Reactive Flows ───────────────────────────────────────────────────────

    val allTasks: StateFlow<List<Task>> = taskRepo.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTasks: StateFlow<List<Task>> = taskRepo.pendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHabits: StateFlow<List<Habit>> = habitRepo.allHabits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bedtimeRoutine: Flow<List<BedtimeItem>> = bedtimeRepo.getForDateFlow()

    private val _snapshot = MutableStateFlow(DashboardSnapshot())
    val snapshot: StateFlow<DashboardSnapshot> = _snapshot

    private val _streakInfos = MutableStateFlow<List<StreakInfo>>(emptyList())
    val streakInfos: StateFlow<List<StreakInfo>> = _streakInfos

    private val _momentum = MutableStateFlow(0f to "")
    val momentum: StateFlow<Pair<Float, String>> = _momentum

    private val _clusters = MutableStateFlow<List<HabitCluster>>(emptyList())
    val clusters: StateFlow<List<HabitCluster>> = _clusters

    private val _predictions = MutableStateFlow<Map<String, Float>>(emptyMap())
    val predictions: StateFlow<Map<String, Float>> = _predictions

    private val _dayProfile = MutableStateFlow<List<DayOfWeekProfile>>(emptyList())
    val dayProfile: StateFlow<List<DayOfWeekProfile>> = _dayProfile

    private val _categoryBreakdown = MutableStateFlow<List<CategoryBreakdown>>(emptyList())
    val categoryBreakdown: StateFlow<List<CategoryBreakdown>> = _categoryBreakdown

    private val _monthlySummary = MutableStateFlow<List<MonthlySummary>>(emptyList())
    val monthlySummary: StateFlow<List<MonthlySummary>> = _monthlySummary

    private val _habit30dSeries = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val habit30dSeries: StateFlow<List<Pair<String, Float>>> = _habit30dSeries

    private val _taskAges = MutableStateFlow<List<TaskAge>>(emptyList())
    val taskAges: StateFlow<List<TaskAge>> = _taskAges

    private val _weeklyMomentum = MutableStateFlow<List<WeeklyMomentum>>(emptyList())
    val weeklyMomentum: StateFlow<List<WeeklyMomentum>> = _weeklyMomentum

    private val _taskSentiments = MutableStateFlow<List<TaskSentiment>>(emptyList())
    val taskSentiments: StateFlow<List<TaskSentiment>> = _taskSentiments

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
                        You are an AI task categorizer and prioritizer. Review the following pending tasks and assign a priority level (High, Medium, or Low) and a category (Work, Personal, Health, Finance, Errand) to each task. 
                        Respond strictly with a JSON object where the keys are the task IDs and the values are objects with "priority" and "category" string properties.
                        Example: {"taskId1": {"priority": "High", "category": "Work"}}
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
                        .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
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
                                        Log.d("DashboardViewModel", "Clean AI Response: $cleanContent")
                                        val updatesMap = JSONObject(cleanContent)
                                        var tasksChanged = false
                                        
                                        val updates = currentPendingTasks.mapNotNull { task ->
                                        if (updatesMap.has(task.id)) {
                                            val taskUpdate = updatesMap.getJSONObject(task.id)
                                            val newPrio = if (taskUpdate.has("priority")) taskUpdate.getString("priority").lowercase().replaceFirstChar { it.uppercase() } else task.priority
                                            val newCat = if (taskUpdate.has("category")) taskUpdate.getString("category").lowercase().replaceFirstChar { it.uppercase() } else task.category
                                            
                                            val finalPrio = if (newPrio in listOf("High", "Medium", "Low")) newPrio else "Medium"
                                            val finalCat = if (newCat in listOf("Work", "Personal", "Health", "Finance", "Errand")) newCat else "Personal"
                                            
                                            tasksChanged = true
                                            task.copy(priority = finalPrio, category = finalCat)
                                        } else null
                                    }
                                    
                                    if (tasksChanged) {
                                        updates.forEach { updatedTask ->
                                            taskRepo.update(updatedTask)
                                        }
                                        refreshSnapshot()
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(getApplication(), "AI Categorization Complete!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Log.e("DashboardViewModel", "AI returned valid JSON but no matching task IDs found!")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(getApplication(), "Failed to update categories. Try again.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.e("DashboardViewModel", "Error fetching prioritization: ${response.code}")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(getApplication(), "AI Error: HTTP ${response.code}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Exception during auto prioritization", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "AI Exception: ${e.message?.take(30)}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sends a single newly-created task to the AI for a final priority/category
     * check.  Uses the same Groq endpoint as [autoPrioritizeTasks] but only
     * touches the one task so we never re-classify tasks the user already edited.
     */
    private fun autoPrioritizeSingleTask(taskName: String) {
        viewModelScope.launch {
            try {
                // Find the task we just created by its name
                val task = taskRepo.search(taskName).firstOrNull { !it.done } ?: return@launch

                val prompt = """
                    You are a task classifier. Given a single task name, reply ONLY with a JSON object:
                    {"priority":"<High|Medium|Low>","category":"<Work|Personal|Health|Finance|Errand|Learning|Social>"}
                    Task: "${task.task}"
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
                    .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                    .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: return@use
                            val json = JSONObject(body)
                            val rawContent = json.getJSONArray("choices")
                                .getJSONObject(0).getJSONObject("message").getString("content")

                            val start = rawContent.indexOf("{")
                            val end   = rawContent.lastIndexOf("}")
                            if (start == -1 || end == -1) return@use

                            val result = JSONObject(rawContent.substring(start, end + 1))

                            val rawPrio = result.optString("priority")
                                .replaceFirstChar { it.uppercase() }
                            val rawCat  = result.optString("category")
                                .replaceFirstChar { it.uppercase() }

                            val aiPriority = if (rawPrio in TaskClassifier.VALID_PRIORITIES) rawPrio else task.priority
                            val aiCategory = if (rawCat  in TaskClassifier.VALID_CATEGORIES) rawCat  else task.category

                            // Only update if the AI changed something
                            if (aiPriority != task.priority || aiCategory != task.category) {
                                taskRepo.update(task.copy(priority = aiPriority, category = aiCategory))
                                refreshSnapshot()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "AI single-task classification skipped: ${e.message}")
                // Silently ignored — local classifier result stays
            }
        }
    }

    init {
        refreshAll()
        viewModelScope.launch {
            if (bedtimeRepo.getForDate().isEmpty()) {
                bedtimeRepo.initializeDefaultRoutine()
            }
        }
    }

    // ── Task CRUD ────────────────────────────────────────────────────────────

    fun createTask(name: String, category: String? = null, priority: String? = null) {
        viewModelScope.launch {
            // 1. Instant offline classification — gives a smart result immediately
            val local = TaskClassifier.classify(name)
            val initialCategory = category ?: local.category
            val initialPriority  = priority  ?: local.priority

            taskRepo.create(name, initialCategory, initialPriority)
            refreshSnapshot()

            // 2. AI refines in background (only sends this specific new task)
            kotlinx.coroutines.delay(300)
            autoPrioritizeSingleTask(name)
        }
    }

    fun markTaskDone(taskId: String, done: Boolean = true) {
        viewModelScope.launch {
            taskRepo.markDone(taskId, done)
            refreshSnapshot()
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepo.delete(taskId)
            refreshSnapshot()
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            taskRepo.update(task)
            refreshSnapshot()
        }
    }

    // ── Habit CRUD ───────────────────────────────────────────────────────────

    fun createHabit(name: String, emoji: String = "🎯", category: String = "Health",
                    targetDays: List<Int> = listOf(1,2,3,4,5,6,7)) {
        viewModelScope.launch {
            habitRepo.create(name, emoji, category, targetDays)
            refreshSnapshot()
        }
    }

    fun toggleHabit(habitId: String) {
        viewModelScope.launch {
            habitRepo.toggle(habitId)
            refreshSnapshot()
        }
    }

    // ── Bedtime CRUD ─────────────────────────────────────────────────────────

    fun addBedtimeItem(name: String) {
        viewModelScope.launch {
            val currentItems = bedtimeRepo.getForDate()
            val newIndex = (currentItems.maxOfOrNull { it.orderIndex } ?: -1) + 1
            bedtimeRepo.upsert(BedtimeItem(name = name, orderIndex = newIndex))
        }
    }

    fun deleteBedtimeItem(itemId: String) {
        viewModelScope.launch {
            bedtimeRepo.delete(itemId)
        }
    }

    fun toggleBedtimeItem(itemId: String, checked: Boolean) {
        viewModelScope.launch {
            bedtimeRepo.setChecked(itemId, checked)
        }
    }

    // ── Voice Command Execution ──────────────────────────────────────────────

    fun executeVoiceCommand(result: VoiceCommandResult) {
        viewModelScope.launch {
            when (result.intent) {
                Intent.ADD_TASK -> {
                    val name = result.entityName ?: return@launch
                    // Use local classifier as fallback when voice AI didn't return values
                    val local = TaskClassifier.classify(name)
                    taskRepo.create(
                        name,
                        result.category ?: local.category,
                        result.priority ?: local.priority
                    )
                }
                Intent.COMPLETE_TASK -> {
                    val name = result.entityName ?: return@launch
                    val tasks = taskRepo.search(name)
                    tasks.firstOrNull()?.let { taskRepo.markDone(it.id) }
                }
                Intent.ADD_HABIT -> {
                    val name = result.entityName ?: return@launch
                    habitRepo.create(name, category = result.category ?: "Health")
                }
                Intent.TOGGLE_HABIT -> {
                    val name = result.entityName ?: return@launch
                    val habits = habitRepo.search(name)
                    habits.firstOrNull()?.let { habitRepo.toggle(it.id) }
                }
                else -> { }
            }
            refreshSnapshot()
        }
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    fun refreshAll() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                refreshSnapshot()
                refreshAnalytics()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun refreshSnapshot() {
        _snapshot.value = analytics.todaySnapshot()
    }

    private suspend fun refreshAnalytics() {
        _streakInfos.value = analytics.getAllStreakInfos()
        _momentum.value = analytics.computeMomentumScore()
        _categoryBreakdown.value = analytics.getCategoryBreakdown()
        _dayProfile.value = analytics.getDayOfWeekProfile()
        try {
            _clusters.value = analytics.getHabitClusters()
            _predictions.value = analytics.getPredictions()
        } catch (_: Exception) {}
        try {
            _monthlySummary.value = analytics.getMonthlySummary()
            _habit30dSeries.value = analytics.getHabit30dTimeSeries()
            _taskAges.value = analytics.getTaskAgeDistribution()
            _weeklyMomentum.value = analytics.getWeeklyMomentumHistory()
            val tasks = taskRepo.search("")  // all tasks
            _taskSentiments.value = SentimentMoodTracker.taskSentimentBreakdown(tasks)
        } catch (_: Exception) {}
    }
}
