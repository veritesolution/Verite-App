package com.example.myapplication.tmr.data.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.myapplication.tmr.data.network.VeriteClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "VeriteAudioPlayer"

/**
 * Audio player that streams WAV from the server and plays via MediaPlayer.
 * Downloads to cache file first (MediaPlayer needs seekable source for WAV).
 */
class VeriteAudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentConcept = MutableStateFlow("")
    val currentConcept: StateFlow<String> = _currentConcept.asStateFlow()

    /**
     * Play audio from a server URL path (e.g. "/audio/study/photosynthesis").
     * Downloads WAV to cache, then plays via MediaPlayer.
     */
    suspend fun playFromUrl(urlPath: String, conceptKey: String = "") {
        stop()
        _currentConcept.value = conceptKey

        withContext(Dispatchers.IO) {
            try {
                val fullUrl = "${VeriteClient.baseUrl.trimEnd('/')}$urlPath"
                val cacheFile = File(context.cacheDir, "verite_audio_${conceptKey.hashCode()}.wav")

                // Download WAV to cache
                val client = VeriteClient.buildWsHttpClient() // reuse OkHttp
                val request = okhttp3.Request.Builder()
                    .url(fullUrl)
                    .addHeader("X-API-Key", VeriteClient.apiKey)
                    .build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e(TAG, "Audio download failed: ${response.code}")
                    return@withContext
                }

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Play from cache file
                withContext(Dispatchers.Main) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(cacheFile.absolutePath)
                        setOnPreparedListener {
                            start()
                            _isPlaying.value = true
                        }
                        setOnCompletionListener {
                            _isPlaying.value = false
                            _currentConcept.value = ""
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                            _isPlaying.value = false
                            true
                        }
                        prepareAsync()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio playback failed: ${e.message}")
                _isPlaying.value = false
            }
        }
    }

    fun stop() {
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            it.release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentConcept.value = ""
    }

    fun release() {
        stop()
    }
}
