package com.example.myapplication.data.local

import androidx.room.*
import com.example.myapplication.data.model.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices")
    fun getAllDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE id = :deviceId")
    suspend fun getDeviceById(deviceId: Int): Device?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<Device>)

    @Update
    suspend fun updateDevice(device: Device)

    @Delete
    suspend fun deleteDevice(device: Device)

    @Query("UPDATE devices SET isConnected = :isConnected WHERE id = :deviceId")
    suspend fun updateConnectionStatus(deviceId: Int, isConnected: Boolean)

    @Query("SELECT * FROM devices WHERE isConnected = 1 LIMIT 1")
    suspend fun getConnectedDevice(): Device?
}
