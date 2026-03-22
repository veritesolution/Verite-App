package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.RecoveryPlan
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.RecoveryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class AiPlanState {
    object Idle : AiPlanState()
    object Loading : AiPlanState()
    data class Success(val plan: String, val planId: Long) : AiPlanState()
    data class Error(val message: String) : AiPlanState()
}

class AiViewModel(
    private val aiRepository: AiRepository,
    private val recoveryRepository: RecoveryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<AiPlanState>(AiPlanState.Idle)
    val uiState: StateFlow<AiPlanState> = _uiState.asStateFlow()
    
    fun generatePlan(
        ailmentType: String,
        frequency: String,
        reasonForAilment: String,
        duration: String,
        reasonForStopping: String,
        emotionContext: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = AiPlanState.Loading
            try {
                aiRepository.generate21DayPlan(
                    ailmentType,
                    frequency,
                    reasonForAilment,
                    duration,
                    reasonForStopping,
                    emotionContext
                ).collect { result ->
                    result.fold(
                        onSuccess = { plan ->
                            // Update UI first, saving is done by Activity on button click
                            // Note: We are NO LONGER saving automatically here to prevent DB locks
                            _uiState.value = AiPlanState.Success(plan, -1L) // Using -1L as a placeholder if planId logic is now strictly handled by AiPlanActivity
                        },
                        onFailure = { error ->
                            android.util.Log.e("AiViewModel", "Plan generation failed", error)
                            _uiState.value = AiPlanState.Error(error.localizedMessage ?: "Failed to generate plan")
                        }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AiViewModel", "Exception in generatePlan", e)
                _uiState.value = AiPlanState.Error(e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }

    fun resetState() {
        _uiState.value = AiPlanState.Idle
    }
}

class AiViewModelFactory(
    private val aiRepository: AiRepository,
    private val recoveryRepository: RecoveryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiViewModel(aiRepository, recoveryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
