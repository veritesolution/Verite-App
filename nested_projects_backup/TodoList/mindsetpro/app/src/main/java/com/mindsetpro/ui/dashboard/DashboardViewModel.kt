package com.mindsetpro.ui.dashboard

import android.app.Application
import androidx.lifecycle.*
import com.mindsetpro.data.local.MindSetDatabase
import com.mindsetpro.data.model.*
import com.mindsetpro.data.repository.HabitRepository
import com.mindsetpro.data.repository.TaskRepository
import com.mindsetpro.ml.AnalyticsEngine
import com.mindsetpro.ml.SentimentMoodTracker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Central ViewModel powering the MindSet Pro dashboard.
 * Exposes reactive state for all UI screens.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MindSetDatabase.getInstance(application)
    private val taskRepo = TaskRepository(db.taskDao())
    private val habitRepo = HabitRepository(db.habitDao(), db.habitCompletionDao())
    val analytics = AnalyticsEngine(habitRepo, taskRepo)

    // ── Reactive Flows ───────────────────────────────────────────────────────

    val allTasks: StateFlow<List<Task>> = taskRepo.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTasks: StateFlow<List<Task>> = taskRepo.pendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHabits: StateFlow<List<Habit>> = habitRepo.allHabits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        refreshAll()
    }

    // ── Task CRUD ────────────────────────────────────────────────────────────

    fun createTask(name: String, category: String = "Work", priority: String = "Medium") {
        viewModelScope.launch {
            taskRepo.create(name, category, priority)
            refreshSnapshot()
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

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            habitRepo.delete(habitId)
            refreshSnapshot()
        }
    }

    // ── Voice Command Execution ──────────────────────────────────────────────

    fun executeVoiceCommand(result: VoiceCommandResult) {
        viewModelScope.launch {
            when (result.intent) {
                Intent.ADD_TASK -> {
                    val name = result.entityName ?: return@launch
                    taskRepo.create(name, result.category ?: "Work", result.priority ?: "Medium")
                }
                Intent.COMPLETE_TASK -> {
                    val name = result.entityName ?: return@launch
                    val tasks = taskRepo.search(name)
                    tasks.firstOrNull()?.let { taskRepo.markDone(it.id) }
                }
                Intent.DELETE_TASK -> {
                    val name = result.entityName ?: return@launch
                    val tasks = taskRepo.search(name)
                    tasks.firstOrNull()?.let { taskRepo.delete(it.id) }
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
                else -> { /* QUERY intents are handled by UI directly */ }
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

        // ML (only if enough data)
        try {
            _clusters.value = analytics.getHabitClusters()
            _predictions.value = analytics.getPredictions()
        } catch (_: Exception) {}
    }
}
