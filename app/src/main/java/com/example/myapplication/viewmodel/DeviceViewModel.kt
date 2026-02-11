package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.Device
import com.example.myapplication.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceViewModel(private val repository: DeviceRepository) : ViewModel() {
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    init {
        // Initialize sample devices on first run and collect from repository
        viewModelScope.launch {
            repository.allDevices.collect { deviceList ->
                _devices.value = deviceList
            }
        }
        
        viewModelScope.launch {
            if (_devices.value.isEmpty()) {
                repository.initializeSampleDevices()
            }
        }
    }
    
    fun toggleDeviceConnection(deviceId: Int) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId)
            device?.let {
                repository.updateConnectionStatus(deviceId, !it.isConnected)
            }
        }
    }
    
    fun updateDevice(device: Device) {
        viewModelScope.launch {
            repository.updateDevice(device)
        }
    }
}

class DeviceViewModelFactory(
    private val repository: DeviceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeviceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
