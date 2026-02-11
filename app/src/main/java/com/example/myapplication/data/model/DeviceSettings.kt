package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_settings")
data class DeviceSettings(
    @PrimaryKey
    val deviceId: Int,
    val temperature: Int = 24, // Default 24°C
    val vibrationMode: VibrationMode = VibrationMode.OFF,
    val thermalSensorsEnabled: Boolean = true,
    val pressureSensorsEnabled: Boolean = true,
    val vibrationMotorsEnabled: Boolean = true,
    val coolingFlowLevel: Int = 50 // 0-100
)
