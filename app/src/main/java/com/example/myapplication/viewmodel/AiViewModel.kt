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
        addictionType: String,
        frequency: String,
        reasonForAddiction: String,
        duration: String,
        reasonForStopping: String
    ) {
        viewModelScope.launch {
            _uiState.value = AiPlanState.Loading
            aiRepository.generate21DayPlan(
                addictionType,
                frequency,
                reasonForAddiction,
                duration,
                reasonForStopping
            ).collect { result ->
                result.fold(
                    onSuccess = { plan ->
                        // Save to database
                        val planId = recoveryRepository.saveActivePlan(
                            RecoveryPlan(
                                addictionType = addictionType,
                                fullPlanText = plan,
                                frequency = frequency,
                                reasonForAddiction = reasonForAddiction,
                                duration = duration,
                                reasonForStopping = reasonForStopping
                            )
                        )
                        _uiState.value = AiPlanState.Success(plan, planId)
                    },
                    onFailure = { error ->
                        _uiState.value = AiPlanState.Error(error.localizedMessage ?: "Failed to generate plan")
                    }
                )
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
