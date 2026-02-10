package com.example.myapplication.data.local

import androidx.room.*
import com.example.myapplication.data.model.PowerOffSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM power_off_settings WHERE id = 1")
    fun getSettings(): Flow<PowerOffSettings?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: PowerOffSettings)
    
    @Update
    suspend fun updateSettings(settings: PowerOffSettings)
}
