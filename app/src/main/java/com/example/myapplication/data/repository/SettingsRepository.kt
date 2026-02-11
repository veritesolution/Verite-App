package com.example.myapplication.data.repository

import com.example.myapplication.data.local.SettingsDao
import com.example.myapplication.data.model.PowerOffDuration
import com.example.myapplication.data.model.PowerOffSettings
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val settingsDao: SettingsDao) {
    
    val settings: Flow<PowerOffSettings?> = settingsDao.getSettings()
    
    suspend fun updateSelectedDuration(duration: PowerOffDuration) {
        val settings = PowerOffSettings(selectedDuration = duration)
        settingsDao.insertSettings(settings)
    }
    
    suspend fun initializeDefaultSettings() {
        val defaultSettings = PowerOffSettings(
            selectedDuration = PowerOffDuration.THIRTY_MINUTES
        )
        settingsDao.insertSettings(defaultSettings)
    }
}
