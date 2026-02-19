package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recovery_plans")
data class RecoveryPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val addictionType: String,
    val fullPlanText: String,
    val frequency: String = "",
    val reasonForAddiction: String = "",
    val duration: String = "",
    val reasonForStopping: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val currentDay: Int = 1,
    val dailyFocusMinutes: Int = 135, // Default 2h 15m as shown in design
    val completedFocusMinutes: Int = 0
)
