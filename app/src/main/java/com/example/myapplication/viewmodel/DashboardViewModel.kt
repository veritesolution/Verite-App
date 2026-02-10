package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.RecoveryPlan
import com.example.myapplication.data.repository.RecoveryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

data class DashboardUiState(
    val activePlan: RecoveryPlan? = null,
    val currentDay: Int = 0,
    val todaysFocus: String = "Complete your focus sessions to start your recovery journey.",
    val todaysAction: String = "Start your first session today.",
    val todaysMotivation: String = "One day at a time. You've got this!",
    val generalTips: List<String> = emptyList(),
    val weeklyProgress: List<Float> = listOf(0.1f, 0.3f, 0.2f, 0.5f, 0.4f, 0.6f, 0.05f)
)

class DashboardViewModel(private val repository: RecoveryRepository) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = repository.activePlan.map { plan ->
        if (plan == null) {
            DashboardUiState()
        } else {
            val daysDiff = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - plan.startDate).toInt() + 1
            val currentDay = daysDiff.coerceIn(1, 21)
            
            val (focus, action, motivation) = parsePlanForDay(plan.fullPlanText, currentDay)
            val tips = parseGeneralTips(plan.fullPlanText)
            
            DashboardUiState(
                activePlan = plan,
                currentDay = currentDay,
                todaysFocus = focus,
                todaysAction = action,
                todaysMotivation = motivation,
                generalTips = tips
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    private fun parsePlanForDay(fullPlanText: String, day: Int): Triple<String, String, String> {
        return try {
            val dayMarker = "Day $day"
            val nextDayMarker = "Day ${day + 1}"
            
            val dayContent = if (fullPlanText.contains(nextDayMarker)) {
                fullPlanText.substringAfter(dayMarker).substringBefore(nextDayMarker)
            } else if (fullPlanText.contains("General Practice Tips")) {
                fullPlanText.substringAfter(dayMarker).substringBefore("General Practice Tips")
            } else {
                fullPlanText.substringAfter(dayMarker)
            }

            val focus = dayContent.lines()
                .firstOrNull { it.contains("Focus", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.removePrefix("-")?.trim()
                ?: "Recovery focus for today."

            val action = dayContent.lines()
                .firstOrNull { it.contains("Action", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.removePrefix("-")?.trim()
                ?: "Take consistent steps forward."

            val motivation = dayContent.lines()
                .firstOrNull { it.contains("Motivation", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.removePrefix("-")?.trim()
                ?: "Believe in yourself, you're doing great!"

            Triple(focus, action, motivation)
        } catch (e: Exception) {
            Triple("Focus on your recovery journey.", "Take positive actions today.", "Keep moving forward!")
        }
    }

    private fun parseGeneralTips(fullPlanText: String): List<String> {
        return try {
            if (!fullPlanText.contains("General Practice Tips")) return emptyList()
            
            fullPlanText.substringAfter("General Practice Tips")
                .lines()
                .filter { it.trim().startsWith("-") || it.trim().firstOrNull()?.isDigit() == true }
                .map { it.trim().removePrefix("-").trim()
                    .replace(Regex("^\\d+\\.\\s*"), "") // Remove leading numbers like "1. "
                    .trim() 
                }
                .filter { it.isNotEmpty() }
                .take(5)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class DashboardViewModelFactory(
    private val repository: RecoveryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
