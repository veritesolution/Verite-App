package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = false)
    val id: Int = 1, // Single user app, ID is always 1
    val name: String,
    val email: String,
    val profileImagePath: String? = null,
    val joinDate: Long = System.currentTimeMillis()
)
