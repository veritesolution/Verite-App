// ═══════════════════════════════════════════════════════════════
// ViewModel for Psychologist chat — REST + WebSocket support
// ═══════════════════════════════════════════════════════════════

package com.example.myapplication.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.*
import com.example.myapplication.data.remote.PsychWebSocketClient
import com.example.myapplication.data.remote.PsychWSEvent
import com.example.myapplication.data.repository.PsychResult
import com.example.myapplication.data.repository.PsychRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── UI State ─────────────────────────────────────────────────

data class PsychChatMessage(
    val content: String,
    val isUser: Boolean,
    val turn: Int = 0,
    val analysis: PsychAnalysisInfo? = null,
    val safety: PsychSafetyInfo? = null,
    val metrics: PsychMetricsInfo? = null,
    val isCrisis: Boolean = false,
    val resources: List<PsychCrisisResource>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class PsychChatUiState(
    val messages: List<PsychChatMessage> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val currentDomain: String = "unknown",
    val currentPhase: String = "intake",
    val isCrisisActive: Boolean = false,
)

// ── ViewModel ────────────────────────────────────────────────

class PsychChatViewModel(
    private val repository: PsychRepository = PsychRepository(),
    private val useWebSocket: Boolean = false
) : ViewModel() {

    private val _uiState = MutableStateFlow(PsychChatUiState())
    val uiState: StateFlow<PsychChatUiState> = _uiState.asStateFlow()

    private var wsClient: PsychWebSocketClient? = null

    init {
        if (useWebSocket) {
            setupWebSocket()
        }
    }

    // ═══════════════════════════════════════════════════════
    // REST-based chat
    // ═══════════════════════════════════════════════════════

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = PsychChatMessage(content = text, isUser = true)
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
            is PsychResult.Success -> {
                val resp = result.data
                val botMsg = PsychChatMessage(
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
            is PsychResult.Error -> {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = result.message,
                )}
            }
            is PsychResult.Loading -> { /* Already showing loading */ }
        }
    }

    // ═══════════════════════════════════════════════════════
    // WebSocket-based chat
    // ═══════════════════════════════════════════════════════

    private fun setupWebSocket() {
        wsClient = PsychWebSocketClient()

        viewModelScope.launch {
            wsClient?.events?.collect { event ->
                when (event) {
                    is PsychWSEvent.Connected -> {
                        _uiState.update { it.copy(
                            sessionId = event.sessionId,
                            isConnected = true,
                            error = null,
                        )}
                    }
                    is PsychWSEvent.MessageReceived -> {
                        val resp = event.response
                        val botMsg = PsychChatMessage(
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
                    is PsychWSEvent.CrisisDetected -> {
                        val resp = event.response
                        val crisisMsg = PsychChatMessage(
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
                    is PsychWSEvent.Error -> {
                        _uiState.update { it.copy(
                            isLoading = false,
                            error = event.message,
                        )}
                    }
                    is PsychWSEvent.Disconnected -> {
                        _uiState.update { it.copy(isConnected = false) }
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
            _uiState.update { PsychChatUiState() }
            if (useWebSocket) {
                wsClient?.disconnect()
                wsClient?.connect()
            } else {
                when (val result = repository.createSession()) {
                    is PsychResult.Success -> {
                        _uiState.update { it.copy(sessionId = result.data.sessionId) }
                    }
                    is PsychResult.Error -> {
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient?.disconnect()
    }
}
