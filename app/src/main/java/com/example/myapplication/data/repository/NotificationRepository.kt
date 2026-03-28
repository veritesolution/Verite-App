package com.example.myapplication.data.repository

import android.content.Context
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.local.NotificationDao
import com.example.myapplication.data.model.AppNotification
import com.example.myapplication.data.model.NotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Central notification repository — single entry point for ALL app notifications.
 * Any component can call NotificationRepository.getInstance(context) to push notifications.
 */
class NotificationRepository private constructor(context: Context) {

    private val dao: NotificationDao =
        AppDatabase.getDatabase(context.applicationContext).notificationDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Observe ─────────────────────────────────────────────
    val allNotifications: Flow<List<AppNotification>> = dao.getRecentNotifications(100)
    val unreadCount: Flow<Int> = dao.getUnreadCount()

    // ── Push Notifications ──────────────────────────────────

    fun pushNotification(
        title: String,
        message: String,
        type: NotificationType = NotificationType.INFO,
        actionRoute: String? = null,
        groupKey: String? = null
    ) {
        scope.launch {
            dao.insert(
                AppNotification(
                    title = title,
                    message = message,
                    type = type,
                    actionRoute = actionRoute,
                    groupKey = groupKey
                )
            )
        }
    }

    // ── Convenience shortcuts ───────────────────────────────

    fun pushError(title: String, message: String, actionRoute: String? = null) =
        pushNotification(title, message, NotificationType.ERROR, actionRoute)

    fun pushSuccess(title: String, message: String, actionRoute: String? = null) =
        pushNotification(title, message, NotificationType.SUCCESS, actionRoute)

    fun pushWarning(title: String, message: String, actionRoute: String? = null) =
        pushNotification(title, message, NotificationType.WARNING, actionRoute)

    fun pushAiInsight(title: String, message: String, actionRoute: String? = null) =
        pushNotification(title, message, NotificationType.AI_INSIGHT, actionRoute)

    fun pushDeviceUpdate(title: String, message: String, actionRoute: String? = null) =
        pushNotification(title, message, NotificationType.DEVICE, actionRoute)

    fun pushAchievement(title: String, message: String) =
        pushNotification(title, message, NotificationType.ACHIEVEMENT)

    fun pushSleepInsight(title: String, message: String) =
        pushNotification(title, message, NotificationType.SLEEP, "bedtime")

    // ── Actions ─────────────────────────────────────────────

    suspend fun markAsRead(id: Long) = dao.markAsRead(id)

    suspend fun markAllAsRead() = dao.markAllAsRead()

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearAll() = dao.deleteAll()

    /** Prune notifications older than 7 days. */
    suspend fun pruneOld() {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        dao.deleteOlderThan(sevenDaysAgo)
    }

    companion object {
        @Volatile
        private var INSTANCE: NotificationRepository? = null

        fun getInstance(context: Context): NotificationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationRepository(context).also { INSTANCE = it }
            }
        }
    }
}
