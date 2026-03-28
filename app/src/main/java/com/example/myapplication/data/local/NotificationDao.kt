package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.data.model.AppNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: AppNotification): Long

    @Query("SELECT * FROM app_notifications ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Query("SELECT * FROM app_notifications ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentNotifications(limit: Int = 50): Flow<List<AppNotification>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM app_notifications WHERE createdAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM app_notifications")
    suspend fun deleteAll()
}
