package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "power_off_settings")
data class PowerOffSettings(
    @PrimaryKey
    val id: Int = 1, // Single row for settings
    val selectedDuration: PowerOffDuration = PowerOffDuration.THIRTY_MINUTES
)

enum class PowerOffDuration(val displayName: String, val minutes: Int?) {
    THIRTY_MINUTES("30 Minutes Duration", 30),
    ONE_HOUR("01 Hour Duration", 60),
    TWO_HOURS("02 Hour Duration", 120),
    NEVER("Never Power Off", null)
}
