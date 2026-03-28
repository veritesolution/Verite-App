package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a chat session with Oryn.
 * Each session has a unique server-side session ID and stores aggregate metadata.
 */
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverSessionId: String = "",
    val title: String = "New Conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val dominantDomain: String = "unknown",
    val currentPhase: String = "intake",
    val crisisDetected: Boolean = false,
    val isActive: Boolean = true,
    // AI-generated summary (filled when session ends)
    val summary: String? = null,
    val summaryGeneratedAt: Long? = null
)

/**
 * Represents a single chat message within a session.
 * Stores both the display text and rich analysis data from the Verite backend.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val content: String,
    val isUser: Boolean,
    val turn: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    // Analysis data from Verite backend (stored as JSON strings for flexibility)
    val domain: String? = null,
    val phase: String? = null,
    val emotionalIntensity: Float? = null,
    val therapeuticMove: String? = null,
    val isCrisis: Boolean = false,
    val providerUsed: String? = null,
    val latencyMs: Int? = null
)
