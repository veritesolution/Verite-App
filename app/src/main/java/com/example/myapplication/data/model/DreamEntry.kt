package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dream_entries")
data class DreamEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val mood: String, // e.g., "Peaceful", "Neutral", "Intense"
    val timestamp: Long = System.currentTimeMillis()
)
