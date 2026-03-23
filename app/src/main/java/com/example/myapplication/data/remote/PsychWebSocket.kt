// ═══════════════════════════════════════════════════════════════
// OkHttp WebSocket client for real-time Psychologist chat
// ═══════════════════════════════════════════════════════════════

package com.example.myapplication.data.remote

import android.util.Log
import com.example.myapplication.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

sealed class PsychWSEvent {
    data class Connected(val sessionId: String) : PsychWSEvent()
    data class MessageReceived(val response: PsychWSResponse) : PsychWSEvent()
    data class CrisisDetected(val response: PsychWSResponse) : PsychWSEvent()
    data class Error(val message: String) : PsychWSEvent()
    data object Disconnected : PsychWSEvent()
}

class PsychWebSocketClient {

    private val TAG = "PsychWS"
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val eventChannel = Channel<PsychWSEvent>(Channel.BUFFERED)

    val events: Flow<PsychWSEvent> = eventChannel.receiveAsFlow()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(sessionId: String? = null) {
        val token = PsychNetworkModule.tokenManager.accessToken
        if (token == null) {
            eventChannel.trySend(PsychWSEvent.Error("Not authenticated"))
            return
        }

        val baseUrl = PsychNetworkModule.getBaseUrl()
            .replace("http://", "ws://")
            .replace("https://", "wss://")

        val request = Request.Builder()
            .url("${baseUrl}api/v1/ws/chat")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                val authMsg = gson.toJson(PsychWSAuthMessage(token, sessionId))
                ws.send(authMsg)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val response = gson.fromJson(text, PsychWSResponse::class.java)
                    when (response.type) {
                        "session_created" -> {
                            response.sessionId?.let {
                                eventChannel.trySend(PsychWSEvent.Connected(it))
                            }
                        }
                        "response" -> {
                            eventChannel.trySend(PsychWSEvent.MessageReceived(response))
                        }
                        "crisis" -> {
                            eventChannel.trySend(PsychWSEvent.CrisisDetected(response))
                        }
                        "error" -> {
                            eventChannel.trySend(
                                PsychWSEvent.Error(response.error ?: "Unknown error")
                            )
                        }
                        "pong" -> Log.d(TAG, "Pong received")
                        "session_ended" -> Log.d(TAG, "Session ended by server")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    eventChannel.trySend(PsychWSEvent.Error("Failed to parse response"))
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                eventChannel.trySend(PsychWSEvent.Error(t.message ?: "Connection failed"))
                eventChannel.trySend(PsychWSEvent.Disconnected)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                eventChannel.trySend(PsychWSEvent.Disconnected)
            }
        })
    }

    fun sendMessage(content: String) {
        val msg = gson.toJson(PsychWSChatMessage(type = "message", content = content))
        val sent = webSocket?.send(msg) ?: false
        if (!sent) {
            eventChannel.trySend(PsychWSEvent.Error("Failed to send — not connected"))
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
