package com.example.myapplication.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.utils.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)

    private val _habitReminderHour = MutableStateFlow(settingsManager.habitReminderHour)
    val habitReminderHour: StateFlow<Int> = _habitReminderHour.asStateFlow()

    private val _bedtimeHour = MutableStateFlow(settingsManager.bedtimeHour)
    val bedtimeHour: StateFlow<Int> = _bedtimeHour.asStateFlow()

    private val _firebaseSyncEnabled = MutableStateFlow(settingsManager.firebaseSyncEnabled)
    val firebaseSyncEnabled: StateFlow<Boolean> = _firebaseSyncEnabled.asStateFlow()

    private val _llmFallbackEnabled = MutableStateFlow(settingsManager.llmFallbackEnabled)
    val llmFallbackEnabled: StateFlow<Boolean> = _llmFallbackEnabled.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(settingsManager.wakeWordEnabled)
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    fun updateHabitReminderHour(hour: Int) {
        settingsManager.habitReminderHour = hour
        _habitReminderHour.value = hour
    }

    fun updateBedtimeHour(hour: Int) {
        settingsManager.bedtimeHour = hour
        _bedtimeHour.value = hour
    }

    fun toggleFirebaseSync(enabled: Boolean) {
        settingsManager.firebaseSyncEnabled = enabled
        _firebaseSyncEnabled.value = enabled
    }

    fun toggleLlmFallback(enabled: Boolean) {
        settingsManager.llmFallbackEnabled = enabled
        _llmFallbackEnabled.value = enabled
    }

    fun toggleWakeWord(enabled: Boolean) {
        settingsManager.wakeWordEnabled = enabled
        _wakeWordEnabled.value = enabled
    }

    fun exportData() {
        // Placeholder for real export logic
    }

    fun importData() {
        // Placeholder for real import logic
    }

    fun seedDemoData() {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            withContext(Dispatchers.IO) {
                // Seed 90 days of habit completion demo data
                val habitDao = db.habitDao()
                val completionDao = db.habitCompletionDao()
                
                val healthyHabitId = java.util.UUID.randomUUID().toString()
                habitDao.insert(com.example.myapplication.data.model.Habit(
                    id = healthyHabitId,
                    name = "Morning Meditation",
                    emoji = "🧘",
                    category = "Health",
                    targetDays = "1,2,3,4,5,6,7"
                ))
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
        }
    }
}
