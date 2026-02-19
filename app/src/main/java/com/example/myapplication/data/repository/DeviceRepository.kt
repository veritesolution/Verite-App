package com.example.myapplication.data.repository

import com.example.myapplication.data.local.DeviceDao
import com.example.myapplication.data.model.Device
import com.example.myapplication.data.model.DeviceType
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val deviceDao: DeviceDao) {
    
    val allDevices: Flow<List<Device>> = deviceDao.getAllDevices()
    
    suspend fun insertDevice(device: Device) {
        deviceDao.insertDevice(device)
    }
    
    suspend fun insertDevices(devices: List<Device>) {
        deviceDao.insertDevices(devices)
    }
    
    suspend fun updateDevice(device: Device) {
        deviceDao.updateDevice(device)
    }
    
    suspend fun deleteDevice(device: Device) {
        deviceDao.deleteDevice(device)
    }
    
    suspend fun updateConnectionStatus(deviceId: Int, isConnected: Boolean) {
        deviceDao.updateConnectionStatus(deviceId, isConnected)
    }
    
    suspend fun getDeviceById(deviceId: Int): Device? {
        return deviceDao.getDeviceById(deviceId)
    }
    
    suspend fun getConnectedDevice(): Device? {
        return deviceDao.getConnectedDevice()
    }
    
    // Initialize with sample devices
    suspend fun initializeSampleDevices() {
        val sampleDevices = listOf(
            Device(
                id = 1,
                name = "Sleep Band",
                type = DeviceType.SLEEP_BAND,
                isConnected = false,
                imageResource = "sleep_band"
            ),
            Device(
                id = 2,
                name = "Smart Backrest",
                type = DeviceType.SMART_BACKREST,
                isConnected = false,
                imageResource = "smart_backrest"
            ),
            Device(
                id = 3,
                name = "Sleep Band",
                type = DeviceType.SLEEP_BAND,
                isConnected = false,
                imageResource = "sleep_band"
            ),
            Device(
                id = 4,
                name = "Smart Backrest",
                type = DeviceType.SMART_BACKREST,
                isConnected = false,
                imageResource = "smart_backrest"
            )
        )
        deviceDao.insertDevices(sampleDevices)
    }
}
