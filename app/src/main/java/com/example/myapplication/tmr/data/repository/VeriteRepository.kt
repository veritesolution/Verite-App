package com.example.myapplication.tmr.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.myapplication.tmr.data.models.CueInfo
import com.example.myapplication.tmr.data.models.ConfigRequest
import com.example.myapplication.tmr.data.models.ConfigResponse
import com.example.myapplication.tmr.data.models.DocumentResponse
import com.example.myapplication.tmr.data.models.SessionReport
import com.example.myapplication.tmr.data.models.SessionStatusResponse
import com.example.myapplication.tmr.data.models.StartSessionRequest
import com.example.myapplication.tmr.data.models.StartSessionResponse
import com.example.myapplication.tmr.data.models.StopSessionResponse
import com.example.myapplication.tmr.data.models.TickEvent
import com.example.myapplication.tmr.data.network.VeriteApi
import com.example.myapplication.tmr.data.network.VeriteWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

/**
 * VeriteRepository — single source of truth for all Vérité TMR data.
 *
 * Wraps:
 *   [VeriteApi]       — Retrofit REST calls (Dispatchers.IO)
 *   [VeriteWebSocket] — real-time stream (SharedFlow)
 *
 * All network calls are wrapped in [Result] so ViewModels never catch exceptions.
 */
class VeriteRepository(
    private val api:       VeriteApi,
    private val webSocket: VeriteWebSocket,
) {
    // ── Live flows ────────────────────────────────────────────────────────────

    val tickEvents:      SharedFlow<TickEvent>              get() = webSocket.tickEvents
    val cueEvents:       SharedFlow<CueInfo>                get() = webSocket.cueEvents
    val connectionState: StateFlow<VeriteWebSocket.ConnectionState>
                                                            get() = webSocket.connectionState

    // ── Session lifecycle ─────────────────────────────────────────────────────

    suspend fun startSession(request: StartSessionRequest): Result<StartSessionResponse> =
        safeCall { api.startSession(request) }

    suspend fun stopSession(): Result<StopSessionResponse> =
        safeCall { api.stopSession() }

    suspend fun getSessionStatus(): Result<SessionStatusResponse> =
        safeCall { api.getSessionStatus() }

    // ── Config ────────────────────────────────────────────────────────────────

    suspend fun getConfig(): Result<Map<String, Any>> =
        safeCall { api.getConfig() }

    suspend fun updateConfig(config: Map<String, Any>): Result<ConfigResponse> =
        safeCall { api.updateConfig(ConfigRequest(config)) }

    // ── Document upload ───────────────────────────────────────────────────────

    /**
     * Upload a document from an Android content URI.
     *
     * FIX: [Context.getContentResolver().openInputStream] returns null on some
     * URI types (e.g. expired or revoked URIs).  Wrapped in explicit null check
     * rather than !! to produce a meaningful error message.
     */
    suspend fun uploadDocument(context: Context, uri: Uri): Result<DocumentResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val filename = resolveFilename(context, uri) ?: "upload.bin"
                val mime     = context.contentResolver.getType(uri)
                    ?: "application/octet-stream"

                // Copy to cache — needed to get a File for OkHttp RequestBody
                val tmp = File(context.cacheDir, "verite_upload_${System.currentTimeMillis()}_$filename")
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open URI: $uri — stream is null")

                inputStream.use { input ->
                    FileOutputStream(tmp).use { output ->
                        input.copyTo(output)
                    }
                }

                val requestBody = tmp.asRequestBody(mime.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", filename, requestBody)

                try {
                    api.uploadDocument(part).bodyOrThrow()
                } finally {
                    tmp.delete()  // always clean up the temp file
                }
            }
        }

    // ── Report ────────────────────────────────────────────────────────────────

    suspend fun getReport(): Result<SessionReport> =
        safeCall { api.getReport() }

    // ── Health ────────────────────────────────────────────────────────────────

    suspend fun checkHealth(): Result<Map<String, Any>> =
        safeCall { api.health() }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    fun connectWebSocket(scope: CoroutineScope) = webSocket.connect(scope)
    fun disconnectWebSocket()                   = webSocket.disconnect()

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Execute a Retrofit call on IO dispatcher, returning [Result]. */
    private suspend fun <T> safeCall(block: suspend () -> Response<T>): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { block().bodyOrThrow() }
        }

    /** Unwrap a Retrofit [Response] or throw a descriptive exception. */
    private fun <T> Response<T>.bodyOrThrow(): T {
        if (isSuccessful) return body()
            ?: error("Empty response body from server (HTTP ${code()})")
        val errBody = errorBody()?.string()?.take(500) ?: "Unknown error"
        throw Exception("HTTP ${code()} ${message()}: $errBody")
    }

    /** Resolve a display name from an Android content URI. */
    private fun resolveFilename(context: Context, uri: Uri): String? {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && col >= 0) return cursor.getString(col)
        }
        return uri.lastPathSegment
    }
}
