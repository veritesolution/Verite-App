// ═══════════════════════════════════════════════════════════════
// File: ui/chat/ChatViewModel.kt
// ViewModel for the chat screen — handles both REST and WebSocket
// Add to: app/src/main/java/com/yourapp/ui/chat/
//
// Gradle dependencies needed:
//   implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
//   implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
// ═══════════════════════════════════════════════════════════════

package com.yourapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.data.model.*
import com.yourapp.data.remote.VeriteWebSocketClient
import com.yourapp.data.remote.WSEvent
import com.yourapp.data.repository.Result
import com.yourapp.data.repository.VeriteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── UI State ─────────────────────────────────────────────────

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val turn: Int = 0,
    val analysis: AnalysisInfo? = null,
    val safety: SafetyInfo? = null,
    val metrics: MetricsInfo? = null,
    val isCrisis: Boolean = false,
    val resources: List<CrisisResource>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val currentDomain: String = "unknown",
    val currentPhase: String = "intake",
    val isCrisisActive: Boolean = false,
)

// ── ViewModel ────────────────────────────────────────────────

class ChatViewModel(
    private val repository: VeriteRepository = VeriteRepository(),
    private val useWebSocket: Boolean = false  // Toggle REST vs WebSocket
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var wsClient: VeriteWebSocketClient? = null

    init {
        if (useWebSocket) {
            setupWebSocket()
        }
    }

    // ═══════════════════════════════════════════════════════
    // REST-based chat (simpler, recommended for most cases)
    // ═══════════════════════════════════════════════════════

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Add user message to UI immediately
        val userMsg = ChatMessage(content = text, isUser = true)
        _uiState.update { it.copy(
            messages = it.messages + userMsg,
            isLoading = true,
            error = null,
        )}

        viewModelScope.launch {
            if (useWebSocket) {
                wsClient?.sendMessage(text)
            } else {
                sendMessageRest(text)
            }
        }
    }

    private suspend fun sendMessageRest(text: String) {
        when (val result = repository.sendMessage(text, _uiState.value.sessionId)) {
            is Result.Success -> {
                val resp = result.data
                val botMsg = ChatMessage(
                    content = resp.response,
                    isUser = false,
                    turn = resp.turn,
                    analysis = resp.analysis,
                    safety = resp.safety,
                    metrics = resp.metrics,
                    isCrisis = resp.safety.isCrisis,
                )

                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + botMsg,
                        sessionId = resp.sessionId,
                        isLoading = false,
                        currentDomain = resp.analysis.domain,
                        currentPhase = resp.analysis.phase,
                        isCrisisActive = resp.safety.isCrisis,
                    )
                }
            }
            is Result.Error -> {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = result.message,
                )}
            }
            is Result.Loading -> { /* Already showing loading */ }
        }
    }

    // ═══════════════════════════════════════════════════════
    // WebSocket-based chat (real-time, lower latency)
    // ═══════════════════════════════════════════════════════

    private fun setupWebSocket() {
        wsClient = VeriteWebSocketClient()

        viewModelScope.launch {
            wsClient?.events?.collect { event ->
                when (event) {
                    is WSEvent.Connected -> {
                        _uiState.update { it.copy(
                            sessionId = event.sessionId,
                            isConnected = true,
                            error = null,
                        )}
                    }
                    is WSEvent.MessageReceived -> {
                        val resp = event.response
                        val botMsg = ChatMessage(
                            content = resp.content ?: "",
                            isUser = false,
                            analysis = resp.analysis,
                            safety = resp.safety,
                            metrics = resp.metrics,
                        )
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + botMsg,
                                isLoading = false,
                                currentDomain = resp.analysis?.domain ?: state.currentDomain,
                                currentPhase = resp.analysis?.phase ?: state.currentPhase,
                            )
                        }
                    }
                    is WSEvent.CrisisDetected -> {
                        val resp = event.response
                        val crisisMsg = ChatMessage(
                            content = resp.content ?: "",
                            isUser = false,
                            isCrisis = true,
                            resources = resp.resources,
                        )
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + crisisMsg,
                                isLoading = false,
                                isCrisisActive = true,
                            )
                        }
                    }
                    is WSEvent.Error -> {
                        _uiState.update { it.copy(
                            isLoading = false,
                            error = event.message,
                        )}
                    }
                    is WSEvent.Disconnected -> {
                        _uiState.update { it.copy(
                            isConnected = false,
                        )}
                    }
                }
            }
        }
    }

    fun connectWebSocket(existingSessionId: String? = null) {
        wsClient?.connect(existingSessionId)
    }

    // ═══════════════════════════════════════════════════════
    // Session Management
    // ═══════════════════════════════════════════════════════

    fun createNewSession() {
        viewModelScope.launch {
            _uiState.update { ChatUiState() }  // Reset UI
            if (useWebSocket) {
                wsClient?.disconnect()
                wsClient?.connect()
            } else {
                when (val result = repository.createSession()) {
                    is Result.Success -> {
                        _uiState.update { it.copy(
                            sessionId = result.data.sessionId,
                        )}
                    }
                    is Result.Error -> {
                        _uiState.update { it.copy(error = result.message) }
                    }
                    else -> {}
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Feedback
    // ═══════════════════════════════════════════════════════

    fun submitFeedback(turn: Int, isHelpful: Boolean) {
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            repository.submitFeedback(
                sessionId = sessionId,
                turn = turn,
                rating = if (isHelpful) "helpful" else "not_helpful",
            )
        }
    }

    // ═══════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient?.disconnect()
    }
}
