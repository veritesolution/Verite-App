package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.PowerOffDuration
import com.example.myapplication.data.model.PowerOffSettings
import com.example.myapplication.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    
    private val _settings = MutableStateFlow(PowerOffSettings())
    val settings: StateFlow<PowerOffSettings> = _settings.asStateFlow()
    
    init {
        viewModelScope.launch {
            repository.settings.collect { settingsData ->
                if (settingsData == null) {
                    repository.initializeDefaultSettings()
                } else {
                    _settings.value = settingsData
                }
            }
        }
    }
    
    fun updateDuration(duration: PowerOffDuration) {
        viewModelScope.launch {
            repository.updateSelectedDuration(duration)
        }
    }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
