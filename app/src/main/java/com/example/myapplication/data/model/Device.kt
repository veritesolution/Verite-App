package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: DeviceType,
    val isConnected: Boolean = false,
    val imageResource: String = "" // Will use drawable resource name
)

enum class DeviceType {
    SLEEP_BAND,
    SMART_BACKREST
}
