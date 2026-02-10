package com.example.myapplication.data.local

import androidx.room.*
import com.example.myapplication.data.model.DreamEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DreamDao {
    @Query("SELECT * FROM dream_entries ORDER BY timestamp DESC")
    fun getAllDreams(): Flow<List<DreamEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDream(dream: DreamEntry)

    @Delete
    suspend fun deleteDream(dream: DreamEntry)
}
