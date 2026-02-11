package com.example.myapplication.data.logic

import com.example.myapplication.data.bluetooth.BioData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class StressLevel {
    VERY_LOW, LOW, MODERATE, HIGH
}

data class StressState(
    val level: StressLevel,
    val score: Int // 0 - 100
)

class StressDetectionEngine {

    private val _currentStress = MutableStateFlow(StressState(StressLevel.LOW, 25))
    val currentStress: StateFlow<StressState> = _currentStress

    /**
     * Analyzes bio-data to update stress state.
     * 
     * Formula (Simplified for Demo):
     * - Base score from Heart Rate (higher HR = higher stress)
     * - Adjusted by Beta/Alpha ratio (higher ratio = higher mental strain/stress)
     */
    fun analyze(data: BioData) {
        // 1. Calculate HR component (60-100 bpm range mapped to 0-60 score)
        val hrScore = ((data.heartRate - 60).coerceIn(0, 40) / 40f) * 60
        
        // 2. Calculate Brain Wave component (Beta/Alpha ratio)
        // Usually, Alpha is relaxation, Beta is focus/stress.
        val ratio = data.beta / data.alpha
        val brainScore = (ratio / 3f).coerceIn(0f, 1f) * 40 // Max 40 points from brain waves
        
        val totalScore = (hrScore + brainScore).toInt().coerceIn(0, 100)
        
        val level = when {
            totalScore < 30 -> StressLevel.VERY_LOW
            totalScore < 50 -> StressLevel.LOW
            totalScore < 75 -> StressLevel.MODERATE
            else -> StressLevel.HIGH
        }
        
        _currentStress.value = StressState(level, totalScore)
    }
}
