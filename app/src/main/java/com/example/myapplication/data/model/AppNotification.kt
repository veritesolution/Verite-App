package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Notification types matching Vérité feature domains.
 */
enum class NotificationType {
    INFO,           // General informational
    SUCCESS,        // Task completed, goal reached, device connected
    WARNING,        // Low battery, missed habit, weak signal
    ERROR,          // Connection failed, API error, crash recovery
    AI_INSIGHT,     // Oryn AI recommendations, session summaries
    SLEEP,          // Sleep quality, bedtime reminders
    DEVICE,         // Headband status, firmware updates
    ACHIEVEMENT,    // Streaks, milestones, personal bests
    SYSTEM          // App updates, permission requests
}

/**
 * Persistent notification entity stored in Room.
 */
@Entity(
    tableName = "app_notifications",
    indices = [Index("isRead"), Index("createdAt")]
)
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val actionRoute: String? = null,    // Deep link route e.g. "dashboard", "analytics"
    val iconName: String? = null,       // Optional custom icon identifier
    val groupKey: String? = null        // Group related notifications (e.g. "oryn_session_123")
)
