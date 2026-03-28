// ═══════════════════════════════════════════════════════════════
// File: data/remote/VeriteWebSocket.kt
// OkHttp WebSocket client for real-time chat
// Add to: app/src/main/java/com/yourapp/data/remote/
//
// Use WebSocket for real-time chat (lower latency, streaming).
// Use REST endpoints for session management, auth, feedback.
// ═══════════════════════════════════════════════════════════════

package com.yourapp.data.remote

import android.util.Log
import com.google.gson.Gson
import com.yourapp.data.model.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

sealed class WSEvent {
    data class Connected(val sessionId: String) : WSEvent()
    data class MessageReceived(val response: WSResponse) : WSEvent()
    data class CrisisDetected(val response: WSResponse) : WSEvent()
    data class Error(val message: String) : WSEvent()
    data object Disconnected : WSEvent()
}

class VeriteWebSocketClient {

    private val TAG = "VeriteWS"
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val eventChannel = Channel<WSEvent>(Channel.BUFFERED)

    val events: Flow<WSEvent> = eventChannel.receiveAsFlow()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // No timeout for WebSocket
        .pingInterval(30, TimeUnit.SECONDS)      // Keep-alive
        .build()

    fun connect(sessionId: String? = null) {
        val token = NetworkModule.tokenManager.accessToken
        if (token == null) {
            eventChannel.trySend(WSEvent.Error("Not authenticated"))
            return
        }

        val baseUrl = NetworkModule.getBaseUrl()
            .replace("http://", "ws://")
            .replace("https://", "wss://")

        val request = Request.Builder()
            .url("${baseUrl}api/v1/ws/chat")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                // Send auth message
                val authMsg = gson.toJson(WSAuthMessage(token, sessionId))
                ws.send(authMsg)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val response = gson.fromJson(text, WSResponse::class.java)

                    when (response.type) {
                        "session_created" -> {
                            response.sessionId?.let {
                                eventChannel.trySend(WSEvent.Connected(it))
                            }
                        }
                        "response" -> {
                            eventChannel.trySend(WSEvent.MessageReceived(response))
                        }
                        "crisis" -> {
                            eventChannel.trySend(WSEvent.CrisisDetected(response))
                        }
                        "error" -> {
                            eventChannel.trySend(
                                WSEvent.Error(response.error ?: "Unknown error")
                            )
                        }
                        "pong" -> {
                            Log.d(TAG, "Pong received")
                        }
                        "session_ended" -> {
                            Log.d(TAG, "Session ended by server")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    eventChannel.trySend(WSEvent.Error("Failed to parse response"))
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                eventChannel.trySend(WSEvent.Error(t.message ?: "Connection failed"))
                eventChannel.trySend(WSEvent.Disconnected)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                eventChannel.trySend(WSEvent.Disconnected)
            }
        })
    }

    fun sendMessage(content: String) {
        val msg = gson.toJson(WSChatMessage(type = "message", content = content))
        val sent = webSocket?.send(msg) ?: false
        if (!sent) {
            eventChannel.trySend(WSEvent.Error("Failed to send — not connected"))
        }
    }

    fun sendPing() {
        webSocket?.send(gson.toJson(mapOf("type" to "ping")))
    }

    fun endSession() {
        webSocket?.send(gson.toJson(mapOf("type" to "end_session")))
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}
