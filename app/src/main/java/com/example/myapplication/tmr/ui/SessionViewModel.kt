package com.example.myapplication.tmr.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.tmr.data.models.*
import com.example.myapplication.tmr.data.network.VeriteWebSocket.ConnectionState
import com.example.myapplication.tmr.data.repository.VeriteRepository
import com.example.myapplication.tmr.service.VeriteForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import javax.inject.Inject

private const val TAG = "SessionViewModel"
private const val TICK_HISTORY_SIZE = 120

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: VeriteRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _sessionState = MutableStateFlow(SessionUiState())
    val sessionUiState: StateFlow<SessionUiState> = _sessionState.asStateFlow()

    private val _startResponse = MutableStateFlow<UiState<StartSessionResponse>>(UiState.Idle)
    val startResponse: StateFlow<UiState<StartSessionResponse>> = _startResponse.asStateFlow()

    private val _latestTick = MutableStateFlow<TickEvent?>(null)
    val latestTick: StateFlow<TickEvent?> = _latestTick.asStateFlow()

    private val _tickRingBuffer = ArrayDeque<TickEvent>(TICK_HISTORY_SIZE + 1)
    private val _tickHistory = MutableStateFlow<List<TickEvent>>(emptyList())
    val tickHistory: StateFlow<List<TickEvent>> = _tickHistory.asStateFlow()

    val cueEvents: SharedFlow<CueInfo> = repository.cueEvents
    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private val _report = MutableStateFlow<UiState<SessionReport>>(UiState.Idle)
    val report: StateFlow<UiState<SessionReport>> = _report.asStateFlow()

    private val _uploadState = MutableStateFlow<UiState<DocumentResponse>>(UiState.Idle)
    val uploadState: StateFlow<UiState<DocumentResponse>> = _uploadState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var tickCollectorJob: Job? = null
    private var statusPollerJob: Job? = null
    private var foregroundService: VeriteForegroundService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            foregroundService = (binder as? VeriteForegroundService.LocalBinder)?.getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundService = null
            serviceBound = false
        }
    }

    init { collectTicks() }

    fun startSession(
        mode: String = "simulation", wsUri: String = "",
        hours: Double = 8.0, config: Map<String, Any> = emptyMap(),
    ) {
        viewModelScope.launch {
            _startResponse.value = UiState.Loading
            val request = StartSessionRequest(
                mode = mode, wsUri = wsUri, hours = hours,
                config = buildDefaultConfig(mode) + config,
            )
            repository.startSession(request)
                .onSuccess { response ->
                    _startResponse.value = UiState.Success(response)
                    _sessionState.update { it.copy(
                        active = true, sessionId = response.sessionId, mode = response.mode,
                    )}
                    // BUG FIX #1: ONLY the foreground service owns the WebSocket.
                    // The service calls connectWebSocket(serviceScope) in its startSession().
                    // We do NOT call connectWebSocket(viewModelScope) here — that would
                    // overwrite connectScope and break reconnection when Activity dies.
                    startForegroundService()
                    startStatusPoller()
                    Log.i(TAG, "Session started: ${response.sessionId.take(8)}")
                }
                .onFailure { e ->
                    _startResponse.value = UiState.Error(e.message ?: "Start failed")
                    emitError(e.message ?: "Start failed")
                }
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            repository.stopSession()
                .onSuccess { response ->
                    _sessionState.update { it.copy(active = false) }
                    stopForegroundService()
                    statusPollerJob?.cancel()
                    _report.value = UiState.Success(response.finalReport)
                }
                .onFailure { e -> emitError(e.message ?: "Stop failed") }
        }
    }

    fun fetchStatus() {
        viewModelScope.launch {
            repository.getSessionStatus().onSuccess { s ->
                _sessionState.update { it.copy(
                    active = s.active, sessionId = s.sessionId, mode = s.mode,
                    elapsedS = s.elapsedS ?: 0.0, nCues = s.nCues ?: 0,
                )}
            }
        }
    }

    fun uploadDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uploadState.value = UiState.Loading
            repository.uploadDocument(context, uri)
                .onSuccess { _uploadState.value = UiState.Success(it) }
                .onFailure { e ->
                    _uploadState.value = UiState.Error(e.message ?: "Upload failed")
                    emitError(e.message ?: "Upload failed")
                }
        }
    }

    fun applyConfig(overrides: Map<String, Any>) {
        viewModelScope.launch {
            repository.updateConfig(overrides)
                .onFailure { e -> emitError(e.message ?: "Config update failed") }
        }
    }

    fun fetchReport() {
        viewModelScope.launch {
            _report.value = UiState.Loading
            repository.getReport()
                .onSuccess { _report.value = UiState.Success(it) }
                .onFailure { e -> _report.value = UiState.Error(e.message ?: "Failed") }
        }
    }

    fun clearError() { _errorMessage.value = null }

    private fun collectTicks() {
        tickCollectorJob?.cancel()
        tickCollectorJob = viewModelScope.launch {
            repository.tickEvents.collect { tick ->
                _latestTick.value = tick
                if (tick.cue != null) _sessionState.update { it.copy(nCues = it.nCues + 1) }
                synchronized(_tickRingBuffer) {
                    if (_tickRingBuffer.size >= TICK_HISTORY_SIZE) _tickRingBuffer.pollFirst()
                    _tickRingBuffer.addLast(tick)
                    _tickHistory.value = _tickRingBuffer.toList()
                }
            }
        }
    }

    private fun startStatusPoller() {
        statusPollerJob?.cancel()
        statusPollerJob = viewModelScope.launch {
            while (true) { delay(5_000); fetchStatus() }
        }
    }

    private fun startForegroundService() {
        ContextCompat.startForegroundService(appContext, VeriteForegroundService.startIntent(appContext))
        appContext.bindService(
            Intent(appContext, VeriteForegroundService::class.java),
            serviceConnection, Context.BIND_AUTO_CREATE
        )
    }

    /**
     * BUG FIX #2: Capture the service reference BEFORE unbinding.
     * unbindService() can synchronously trigger onServiceDisconnected()
     * on some OEMs, nulling foregroundService before stopSession() runs.
     */
    private fun stopForegroundService() {
        val service = foregroundService  // capture before unbind can null it
        if (serviceBound) {
            try { appContext.unbindService(serviceConnection) } catch (_: Exception) {}
            serviceBound = false
        }
        service?.stopSession()
        foregroundService = null
    }

    private fun emitError(msg: String) { Log.e(TAG, msg); _errorMessage.value = msg }

    private fun buildDefaultConfig(mode: String): Map<String, Any> = buildMap {
        put("phase_predictor", "causal_interp"); put("pac_enabled", true)
        put("kcomplex_enabled", true); put("base_volume", 0.20)
        put("artefact_rejection_enabled", true)
        put("time_warp", if (mode == "simulation") 3000.0 else 1.0)
    }

    override fun onCleared() {
        super.onCleared(); stopForegroundService()
        statusPollerJob?.cancel(); tickCollectorJob?.cancel()
    }
}
