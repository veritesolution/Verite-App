package com.example.myapplication.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.myapplication.data.model.SensorReading

@Dao
interface SensorDao {
    @Insert
    suspend fun insert(reading: SensorReading)

    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC")
    fun getAllReadings(): LiveData<List<SensorReading>>

    @Query("SELECT * FROM sensor_readings WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestReadings(type: String, limit: Int): LiveData<List<SensorReading>>
    
    @Query("DELETE FROM sensor_readings")
    suspend fun deleteAll()
}
