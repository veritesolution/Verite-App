package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.model.ChatMessageEntity
import com.example.myapplication.data.model.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ── Sessions ─────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): ChatSession?

    @Query("SELECT * FROM chat_sessions WHERE serverSessionId = :serverSessionId LIMIT 1")
    suspend fun getSessionByServerId(serverSessionId: String): ChatSession?

    @Query("SELECT * FROM chat_sessions WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveSession(): ChatSession?

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 20): Flow<List<ChatSession>>

    @Query("UPDATE chat_sessions SET isActive = 0, updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun deactivateSession(sessionId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("UPDATE chat_sessions SET summary = :summary, summaryGeneratedAt = :generatedAt WHERE id = :sessionId")
    suspend fun updateSessionSummary(sessionId: Long, summary: String, generatedAt: Long = System.currentTimeMillis())

    // ── Messages ─────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionOnce(sessionId: Long): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: Long): Int

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)

    // ── Analytics queries ────────────────────────────────────

    @Query("SELECT domain, COUNT(*) as count FROM chat_messages WHERE sessionId = :sessionId AND domain IS NOT NULL GROUP BY domain ORDER BY count DESC")
    suspend fun getDomainDistribution(sessionId: Long): List<DomainCount>

    @Query("SELECT AVG(emotionalIntensity) FROM chat_messages WHERE sessionId = :sessionId AND emotionalIntensity IS NOT NULL")
    suspend fun getAvgEmotionalIntensity(sessionId: Long): Float?

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId AND isCrisis = 1")
    suspend fun getCrisisMessageCount(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun getTotalSessionCount(): Int
}

data class DomainCount(
    val domain: String,
    val count: Int
)
