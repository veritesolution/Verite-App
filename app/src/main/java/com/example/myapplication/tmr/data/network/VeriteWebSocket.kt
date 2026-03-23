package com.example.myapplication.tmr.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.example.myapplication.tmr.data.models.CueInfo
import com.example.myapplication.tmr.data.models.TickEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "VeriteWebSocket"

/**
 * Production WebSocket client for the Vérité TMR live stream (/ws/stream).
 *
 * Design
 * ------
 * OkHttp dispatches [onMessage] on its own thread pool.  We must never touch
 * coroutine machinery from that thread in a way that could block or race.
 *
 * Fix: use [MutableSharedFlow.tryEmit] instead of launching a coroutine from
 * the OkHttp thread.  tryEmit() is non-blocking, thread-safe, and succeeds as
 * long as the shared flow's extraBufferCapacity isn't exhausted.  At 64 slots
 * for tick events and 50 ms per frame, the buffer absorbs over 3 seconds of
 * backpressure — more than enough for any UI jank.
 *
 * Reconnection
 * ------------
 * Exponential back-off: 2 s → 4 s → 8 s → 16 s → 30 s (max).
 * Reconnect jobs are always tied to the caller-supplied CoroutineScope so they
 * are cancelled automatically when the ViewModel is cleared.
 *
 * Auth
 * ----
 * The API key is appended as a query parameter: ?api_key=…
 * (WebSocket clients cannot reliably set arbitrary HTTP headers.)
 */
class VeriteWebSocket(private val apiKey: String) {

    private val gson   = Gson()
    private val client: OkHttpClient = VeriteClient.buildWsHttpClient()

    // ── Public flows ──────────────────────────────────────────────────────────

    /** Every ~50 ms brain-state tick from the server. */
    private val _tickEvents = MutableSharedFlow<TickEvent>(extraBufferCapacity = 64)
    val tickEvents: SharedFlow<TickEvent> = _tickEvents.asSharedFlow()

    /** Emitted only when a cue is fired — subset of [tickEvents]. */
    private val _cueEvents = MutableSharedFlow<CueInfo>(extraBufferCapacity = 16)
    val cueEvents: SharedFlow<CueInfo> = _cueEvents.asSharedFlow()

    /** Connection lifecycle state — safe for UI binding. */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── Internal state ────────────────────────────────────────────────────────

    @Volatile private var ws: WebSocket? = null
    private val isConnected  = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    /** Set by [connect]; used to launch reconnect coroutines. */
    private var connectScope: CoroutineScope? = null

    enum class ConnectionState {
        Disconnected, Connecting, Connected, Reconnecting, Error
    }

    // ── Connect / disconnect ──────────────────────────────────────────────────

    /**
     * Open the WebSocket connection.
     *
     * @param scope  Coroutine scope that owns this connection — typically
     *               `viewModelScope`.  Cancelled scope → reconnect jobs stop.
     */
    fun connect(scope: CoroutineScope) {
        connectScope = scope
        openSocket()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        ws?.close(1000, "Client disconnected")
        ws = null
        isConnected.set(false)
        _connectionState.value = ConnectionState.Disconnected
    }

    // ── Internal socket management ────────────────────────────────────────────

    private fun openSocket() {
        if (isConnected.get()) return

        _connectionState.value = ConnectionState.Connecting
        val url     = "${VeriteClient.wsBaseUrl.trimEnd('/')}/ws/stream?api_key=$apiKey"
        val request = Request.Builder().url(url).build()
        Log.i(TAG, "Connecting → $url")

        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                _connectionState.value = ConnectionState.Connected
                Log.i(TAG, "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Server closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closed: $code $reason")
                markDisconnected(reconnect = false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Failure: ${t.message}")
                markDisconnected(reconnect = true)
            }
        })
    }

    private fun markDisconnected(reconnect: Boolean) {
        isConnected.set(false)
        ws = null
        _connectionState.value = if (reconnect) ConnectionState.Error else ConnectionState.Disconnected
        if (reconnect) scheduleReconnect()
    }

    private fun scheduleReconnect(maxAttempts: Int = 5) {
        val scope = connectScope ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive && attempt < maxAttempts && !isConnected.get()) {
                attempt++
                // Exponential back-off: 2 s, 4 s, 8 s, 16 s, 30 s
                val delayMs = minOf(1_000L shl attempt, 30_000L)
                Log.i(TAG, "Reconnect $attempt/$maxAttempts in ${delayMs}ms")
                _connectionState.value = ConnectionState.Reconnecting
                delay(delayMs)
                if (!isConnected.get() && isActive) openSocket()
            }
            if (!isConnected.get()) {
                Log.e(TAG, "Reconnect exhausted after $maxAttempts attempts")
                _connectionState.value = ConnectionState.Error
            }
        }
    }

    // ── Message handling ──────────────────────────────────────────────────────

    /**
     * Called on OkHttp's dispatcher thread — MUST NOT block or launch coroutines
     * that could block.  Uses [MutableSharedFlow.tryEmit] which is non-blocking
     * and thread-safe.
     */
    private fun handleMessage(text: String) {
        val type = extractType(text) ?: return

        when (type) {
            "tick" -> {
                try {
                    val tick = gson.fromJson(text, TickEvent::class.java) ?: return
                    // tryEmit is non-blocking and thread-safe — safe to call from OkHttp thread
                    _tickEvents.tryEmit(tick)
                    tick.cue?.let { _cueEvents.tryEmit(it) }
                } catch (e: JsonSyntaxException) {
                    Log.w(TAG, "Bad tick JSON: ${e.message}")
                }
            }
            "session_ended" -> {
                Log.i(TAG, "Session ended by server")
                _connectionState.value = ConnectionState.Disconnected
            }
            "error" -> {
                Log.e(TAG, "Server error: $text")
                _connectionState.value = ConnectionState.Error
            }
            "ping" -> { /* keepalive — no action needed */ }
            else   -> Log.d(TAG, "Unknown type: $type")
        }
    }

    /**
     * Extract the "type" field via regex without full JSON deserialisation.
     * Avoids allocating a full Gson object for every ping frame.
     */
    private fun extractType(json: String): String? =
        TYPE_REGEX.find(json)?.groupValues?.getOrNull(1)

    companion object {
        private val TYPE_REGEX = Regex(""""type"\s*:\s*"([^"]+)"""")
    }
}
