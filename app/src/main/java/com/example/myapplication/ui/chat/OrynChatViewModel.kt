package com.example.myapplication.ui.chat

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.ChatMessageEntity
import com.example.myapplication.data.model.ChatSession
import com.example.myapplication.data.remote.PsychNetworkModule
import com.example.myapplication.data.repository.ApiMessageResult
import com.example.myapplication.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "OrynChatVM"

// ── UI State ─────────────────────────────────────────────────

data class OrynUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val currentSession: ChatSession? = null,
    val isLoading: Boolean = false,
    val isConnecting: Boolean = true,
    val isAuthenticated: Boolean = false,
    val serverOnline: Boolean = false,
    val error: String? = null,
    val isCrisisActive: Boolean = false,
    val currentDomain: String = "unknown",
    val currentPhase: String = "intake"
)

// ── ViewModel ────────────────────────────────────────────────

class OrynChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(OrynUiState())
    val uiState: StateFlow<OrynUiState> = _uiState.asStateFlow()

    private var messageCollectionJob: Job? = null

    init {
        initializeConnection()
    }

    // ═══════════════════════════════════════════════════════
    // Initialization — server health + auth + session restore
    // ═══════════════════════════════════════════════════════

    private fun initializeConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true) }

            try {
                // Step 1: Initialize network module
                PsychNetworkModule.init(getApplication())

                // Step 2: Check server health with retry
                var serverOnline = false
                repeat(2) { attempt ->
                    if (!isNetworkAvailable()) {
                        Log.w(TAG, "No network (attempt ${attempt + 1})")
                        if (attempt == 0) delay(2000)
                        return@repeat
                    }
                    if (repository.checkServerHealth()) {
                        serverOnline = true
                        return@repeat
                    }
                    if (attempt == 0) delay(2000)
                }

                _uiState.update { it.copy(serverOnline = serverOnline) }

                // Step 3: Auto-authenticate
                if (serverOnline) {
                    val authOk = repository.ensureAuthenticated()
                    _uiState.update { it.copy(isAuthenticated = authOk) }
                    if (authOk) {
                        Log.i(TAG, "Authenticated with Verite backend")
                    }
                }

                // Step 4: Restore or create chat session
                val session = repository.getOrCreateActiveSession()
                _uiState.update { it.copy(currentSession = session) }

                // Step 5: Load existing messages for this session
                startCollectingMessages(session.id)

            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                _uiState.update { it.copy(error = "Failed to initialize: ${e.message}") }
            }

            _uiState.update { it.copy(isConnecting = false) }
        }
    }

    private fun startCollectingMessages(sessionId: Long) {
        messageCollectionJob?.cancel()
        messageCollectionJob = viewModelScope.launch {
            repository.getMessagesForSession(sessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Send Message — with full error handling and retry
    // ═══════════════════════════════════════════════════════

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val session = _uiState.value.currentSession ?: return

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            // Save user message locally
            repository.saveUserMessage(session.id, text)

            if (!isNetworkAvailable()) {
                addOfflineResponse()
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            if (!_uiState.value.serverOnline || !_uiState.value.isAuthenticated) {
                // Try to reconnect
                val online = repository.checkServerHealth()
                if (online) {
                    val authOk = repository.ensureAuthenticated()
                    _uiState.update { it.copy(serverOnline = true, isAuthenticated = authOk) }
                }
                if (!online || !_uiState.value.isAuthenticated) {
                    addServerDownResponse()
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
            }

            // Send to Verite API
            val serverSessionId = _uiState.value.currentSession?.serverSessionId
            when (val result = repository.sendMessageToApi(text, session.id, serverSessionId)) {
                is ApiMessageResult.Success -> {
                    val resp = result.response
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            currentDomain = resp.analysis.domain,
                            currentPhase = resp.analysis.phase,
                            isCrisisActive = state.isCrisisActive || resp.safety.isCrisis,
                            // Refresh session metadata
                            currentSession = state.currentSession?.copy(
                                serverSessionId = resp.sessionId,
                                dominantDomain = resp.analysis.domain,
                                currentPhase = resp.analysis.phase
                            )
                        )
                    }
                }
                is ApiMessageResult.Error -> {
                    if (result.code == 401) {
                        // Re-authenticate and retry once
                        val reAuthOk = repository.ensureAuthenticated()
                        if (reAuthOk) {
                            val retry = repository.sendMessageToApi(text, session.id, serverSessionId)
                            if (retry is ApiMessageResult.Success) {
                                _uiState.update { it.copy(isLoading = false) }
                                return@launch
                            }
                        }
                    }
                    addErrorResponse()
                    _uiState.update { it.copy(isLoading = false) }
                }
                is ApiMessageResult.Loading -> {}
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Fallback Responses (saved to DB for persistence)
    // ═══════════════════════════════════════════════════════

    private suspend fun addOfflineResponse() {
        val session = _uiState.value.currentSession ?: return
        val msg = ChatMessageEntity(
            sessionId = session.id,
            content = "It looks like you're offline right now. I can't connect to my server, " +
                "but I want you to know I'm here. Try checking your WiFi or mobile data.",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        AppDatabase.getDatabase(getApplication()).chatDao().insertMessage(msg)
    }

    private suspend fun addServerDownResponse() {
        val session = _uiState.value.currentSession ?: return
        val msg = ChatMessageEntity(
            sessionId = session.id,
            content = "I'm connecting to my server... Please try sending your message again in a moment.",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        AppDatabase.getDatabase(getApplication()).chatDao().insertMessage(msg)
    }

    private suspend fun addErrorResponse() {
        val session = _uiState.value.currentSession ?: return
        val msg = ChatMessageEntity(
            sessionId = session.id,
            content = "I had trouble processing that. Could you try again? " +
                "I want to make sure I understand you correctly.",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        AppDatabase.getDatabase(getApplication()).chatDao().insertMessage(msg)
    }

    // ═══════════════════════════════════════════════════════
    // Session Management
    // ═══════════════════════════════════════════════════════

    fun startNewSession() {
        viewModelScope.launch {
            val currentSession = _uiState.value.currentSession
            // Generate summary for current session before creating new one
            currentSession?.let {
                if (it.messageCount > 0) {
                    repository.generateSessionSummary(it.id)
                }
                repository.endSession(it.id)
            }

            val newSession = repository.createNewSession()
            _uiState.update {
                it.copy(
                    currentSession = newSession,
                    isCrisisActive = false,
                    currentDomain = "unknown",
                    currentPhase = "intake",
                    error = null
                )
            }
            startCollectingMessages(newSession.id)
        }
    }

    /**
     * End current session and generate AI summary.
     * Returns the session ID for navigating to summary screen.
     */
    fun endSessionAndSummarize(onComplete: (Long?) -> Unit) {
        viewModelScope.launch {
            val session = _uiState.value.currentSession
            if (session != null && session.messageCount > 0) {
                repository.generateSessionSummary(session.id)
                repository.endSession(session.id)
                onComplete(session.id)
            } else {
                onComplete(null)
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun retryConnection() {
        initializeConnection()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun AppDatabase.Companion.getDatabase(app: Application) =
        AppDatabase.getDatabase(app.applicationContext)

    override fun onCleared() {
        super.onCleared()
        messageCollectionJob?.cancel()
    }
}
