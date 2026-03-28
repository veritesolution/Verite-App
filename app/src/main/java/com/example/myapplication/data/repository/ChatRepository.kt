package com.example.myapplication.data.repository

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.local.ChatDao
import com.example.myapplication.data.local.DomainCount
import com.example.myapplication.data.model.ChatMessageEntity
import com.example.myapplication.data.model.ChatSession
import com.example.myapplication.data.model.PsychChatMessageResponse
import com.example.myapplication.data.remote.PsychNetworkModule
import kotlinx.coroutines.flow.Flow

private const val TAG = "ChatRepository"

/**
 * Single source of truth for Oryn chat data.
 * Bridges Room DB (local persistence) with Verite API (backend).
 * Follows Repository pattern for clean MVVM architecture.
 */
class ChatRepository(private val context: Context) {

    private val chatDao: ChatDao = AppDatabase.getDatabase(context).chatDao()
    private val psychRepo: PsychRepository by lazy { PsychRepository() }

    // ── Authentication ───────────────────────────────────────

    /**
     * Auto-authenticate with the Verite backend using a device-based identity.
     * Tries login → register → retry login. Completely seamless.
     */
    suspend fun ensureAuthenticated(): Boolean {
        val tokenManager = PsychNetworkModule.tokenManager
        if (tokenManager.isLoggedIn) return true

        val username = getDeviceUsername()
        val password = "verite_secure_${username}_2026"

        // Try login
        when (psychRepo.login(username, password)) {
            is PsychResult.Success -> return true
            else -> {}
        }

        // Try register
        when (val reg = psychRepo.register(username, password, "Verite User")) {
            is PsychResult.Success -> return true
            is PsychResult.Error -> {
                if (reg.code == 400) {
                    // User exists, retry login
                    return psychRepo.login(username, password) is PsychResult.Success
                }
            }
            else -> {}
        }
        return false
    }

    private fun getDeviceUsername(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        )
        return "verite_${androidId?.take(12) ?: "default"}"
    }

    // ── Health Check ─────────────────────────────────────────

    suspend fun checkServerHealth(): Boolean {
        return when (psychRepo.healthCheck()) {
            is PsychResult.Success -> true
            else -> false
        }
    }

    // ── Session Management ───────────────────────────────────

    /**
     * Get or create an active chat session.
     * Returns the local DB session, creating one if needed.
     */
    suspend fun getOrCreateActiveSession(): ChatSession {
        // Check for existing active session
        chatDao.getActiveSession()?.let { return it }

        // Create new local session
        val session = ChatSession(
            title = "New Conversation",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        val id = chatDao.insertSession(session)
        return session.copy(id = id)
    }

    suspend fun createNewSession(): ChatSession {
        // Deactivate current active session
        chatDao.getActiveSession()?.let { active ->
            chatDao.deactivateSession(active.id)
        }

        val session = ChatSession(
            title = "New Conversation",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        val id = chatDao.insertSession(session)
        return session.copy(id = id)
    }

    suspend fun getSessionById(sessionId: Long): ChatSession? {
        return chatDao.getSessionById(sessionId)
    }

    fun getAllSessions(): Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getRecentSessions(limit: Int = 20): Flow<List<ChatSession>> = chatDao.getRecentSessions(limit)

    suspend fun endSession(sessionId: Long) {
        chatDao.deactivateSession(sessionId)
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteSession(sessionId)
    }

    // ── Message Operations ───────────────────────────────────

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getMessagesOnce(sessionId: Long): List<ChatMessageEntity> {
        return chatDao.getMessagesForSessionOnce(sessionId)
    }

    /**
     * Save a user message to the local database.
     */
    suspend fun saveUserMessage(sessionId: Long, content: String): ChatMessageEntity {
        val message = ChatMessageEntity(
            sessionId = sessionId,
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        val id = chatDao.insertMessage(message)
        updateSessionMetadata(sessionId)
        return message.copy(id = id)
    }

    /**
     * Send a message to the Verite API and save the response locally.
     * Returns the API response or an error message.
     */
    suspend fun sendMessageToApi(
        text: String,
        localSessionId: Long,
        serverSessionId: String?
    ): ApiMessageResult {
        return when (val result = psychRepo.sendMessage(text, serverSessionId)) {
            is PsychResult.Success -> {
                val resp = result.data
                // Save bot response to local DB
                val botMessage = ChatMessageEntity(
                    sessionId = localSessionId,
                    content = resp.response.ifEmpty { "I'm processing your message..." },
                    isUser = false,
                    turn = resp.turn,
                    timestamp = System.currentTimeMillis(),
                    domain = resp.analysis.domain,
                    phase = resp.analysis.phase,
                    emotionalIntensity = resp.analysis.emotionalIntensity,
                    therapeuticMove = resp.analysis.therapeuticMove,
                    isCrisis = resp.safety.isCrisis,
                    providerUsed = resp.metrics.providerUsed,
                    latencyMs = resp.metrics.latencyMs
                )
                chatDao.insertMessage(botMessage)

                // Update session with server session ID and metadata
                val session = chatDao.getSessionById(localSessionId)
                session?.let {
                    chatDao.updateSession(it.copy(
                        serverSessionId = resp.sessionId,
                        updatedAt = System.currentTimeMillis(),
                        dominantDomain = resp.analysis.domain,
                        currentPhase = resp.analysis.phase,
                        crisisDetected = it.crisisDetected || resp.safety.isCrisis,
                        messageCount = chatDao.getMessageCount(localSessionId)
                    ))
                }

                ApiMessageResult.Success(resp)
            }
            is PsychResult.Error -> {
                Log.e(TAG, "API error (${result.code}): ${result.message}")
                ApiMessageResult.Error(result.message, result.code)
            }
            is PsychResult.Loading -> ApiMessageResult.Loading
        }
    }

    private suspend fun updateSessionMetadata(sessionId: Long) {
        val session = chatDao.getSessionById(sessionId) ?: return
        chatDao.updateSession(session.copy(
            updatedAt = System.currentTimeMillis(),
            messageCount = chatDao.getMessageCount(sessionId)
        ))
    }

    // ── Summary Generation ───────────────────────────────────

    /**
     * Generate an AI summary of a chat session by sending the conversation
     * transcript to the Verite API as a summary request.
     */
    suspend fun generateSessionSummary(sessionId: Long): String? {
        val messages = chatDao.getMessagesForSessionOnce(sessionId)
        if (messages.isEmpty()) return null

        // Build a condensed transcript for the AI to summarize
        val transcript = messages.joinToString("\n") { msg ->
            val role = if (msg.isUser) "User" else "Oryn"
            "$role: ${msg.content}"
        }

        val summaryPrompt = "Please provide a brief therapeutic summary of this conversation session. " +
            "Include: key themes discussed, emotional state observed, therapeutic progress, and " +
            "any recommended follow-up topics. Keep it concise (3-5 sentences).\n\n" +
            "Conversation:\n$transcript"

        // Use the Verite API to generate the summary
        val session = chatDao.getSessionById(sessionId)
        return when (val result = psychRepo.sendMessage(summaryPrompt, session?.serverSessionId)) {
            is PsychResult.Success -> {
                val summary = result.data.response
                chatDao.updateSessionSummary(sessionId, summary)
                summary
            }
            is PsychResult.Error -> {
                Log.e(TAG, "Summary generation failed: ${result.message}")
                // Generate a basic local summary as fallback
                val fallbackSummary = generateLocalSummary(messages)
                chatDao.updateSessionSummary(sessionId, fallbackSummary)
                fallbackSummary
            }
            else -> null
        }
    }

    private fun generateLocalSummary(messages: List<ChatMessageEntity>): String {
        val userMessages = messages.filter { it.isUser }
        val botMessages = messages.filter { !it.isUser }
        val domains = botMessages.mapNotNull { it.domain }.groupBy { it }
            .entries.sortedByDescending { it.value.size }
            .map { it.key }
        val crisisCount = messages.count { it.isCrisis }

        return buildString {
            append("Session with ${messages.size} messages (${userMessages.size} from user). ")
            if (domains.isNotEmpty()) {
                append("Topics discussed: ${domains.joinToString(", ")}. ")
            }
            if (crisisCount > 0) {
                append("Crisis signals detected in $crisisCount messages. ")
            }
            append("Session lasted ${formatDuration(messages)}.")
        }
    }

    private fun formatDuration(messages: List<ChatMessageEntity>): String {
        if (messages.size < 2) return "a brief moment"
        val durationMs = messages.last().timestamp - messages.first().timestamp
        val minutes = durationMs / 60000
        return when {
            minutes < 1 -> "less than a minute"
            minutes == 1L -> "about 1 minute"
            minutes < 60 -> "about $minutes minutes"
            else -> "about ${minutes / 60} hours"
        }
    }

    // ── Analytics ─────────────────────────────────────────────

    suspend fun getDomainDistribution(sessionId: Long): List<DomainCount> {
        return chatDao.getDomainDistribution(sessionId)
    }

    suspend fun getAvgEmotionalIntensity(sessionId: Long): Float? {
        return chatDao.getAvgEmotionalIntensity(sessionId)
    }

    suspend fun getCrisisMessageCount(sessionId: Long): Int {
        return chatDao.getCrisisMessageCount(sessionId)
    }

    suspend fun getTotalSessionCount(): Int {
        return chatDao.getTotalSessionCount()
    }
}

sealed class ApiMessageResult {
    data class Success(val response: PsychChatMessageResponse) : ApiMessageResult()
    data class Error(val message: String, val code: Int = 0) : ApiMessageResult()
    data object Loading : ApiMessageResult()
}
