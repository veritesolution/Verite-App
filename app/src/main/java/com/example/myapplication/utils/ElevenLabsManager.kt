package com.example.myapplication.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs API Manager for Vérité Voice Agent.
 * Handles: voice listing, TTS synthesis, voice preview, speaker verification enrollment.
 */
class ElevenLabsManager(private val context: Context) {

    companion object {
        private const val TAG = "ElevenLabsManager"
        private const val BASE_URL = "https://api.elevenlabs.io/v1"
        private const val DEFAULT_MODEL = "eleven_multilingual_v2"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null

    // State flows
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isLoadingVoices = MutableStateFlow(false)
    val isLoadingVoices: StateFlow<Boolean> = _isLoadingVoices.asStateFlow()

    // Data classes
    data class VoiceInfo(
        val voiceId: String,
        val name: String,
        val category: String,   // "premade", "cloned", "generated"
        val description: String?,
        val previewUrl: String?,
        val labels: Map<String, String>   // accent, age, gender, etc.
    )

    data class TtsSettings(
        val stability: Float = 0.5f,
        val similarityBoost: Float = 0.75f,
        val style: Float = 0.0f,
        val useSpeakerBoost: Boolean = true
    )

    private fun apiKey(): String = com.example.myapplication.Secrets.ELEVENLABS_API_KEY

    // ── Voice Listing ──────────────────────────────────────────────────

    suspend fun getAvailableVoices(): List<VoiceInfo> = withContext(Dispatchers.IO) {
        _isLoadingVoices.value = true
        try {
            val request = Request.Builder()
                .url("$BASE_URL/voices")
                .addHeader("Accept", "application/json")
                .addHeader("xi-api-key", apiKey())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch voices: ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val voicesArray = json.getJSONArray("voices")

                val voices = mutableListOf<VoiceInfo>()
                for (i in 0 until voicesArray.length()) {
                    val v = voicesArray.getJSONObject(i)
                    val labelsObj = v.optJSONObject("labels")
                    val labelsMap = mutableMapOf<String, String>()
                    labelsObj?.keys()?.forEach { key ->
                        labelsMap[key] = labelsObj.optString(key, "")
                    }

                    voices.add(VoiceInfo(
                        voiceId = v.getString("voice_id"),
                        name = v.getString("name"),
                        category = v.optString("category", "premade"),
                        description = v.optString("description", null),
                        previewUrl = v.optString("preview_url", null),
                        labels = labelsMap
                    ))
                }
                voices
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching voices", e)
            emptyList()
        } finally {
            _isLoadingVoices.value = false
        }
    }

    // ── Text-to-Speech ──────────────────────────────────────────────────

    /**
     * Synthesize text to speech and play it immediately.
     * Returns true if successful.
     */
    suspend fun speak(
        text: String,
        voiceId: String,
        settings: TtsSettings = TtsSettings()
    ): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false

        _isSpeaking.value = true
        try {
            val bodyJson = JSONObject().apply {
                put("text", text)
                put("model_id", DEFAULT_MODEL)
                put("voice_settings", JSONObject().apply {
                    put("stability", settings.stability.toDouble())
                    put("similarity_boost", settings.similarityBoost.toDouble())
                    put("style", settings.style.toDouble())
                    put("use_speaker_boost", settings.useSpeakerBoost)
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL/text-to-speech/$voiceId")
                .addHeader("Accept", "audio/mpeg")
                .addHeader("Content-Type", "application/json")
                .addHeader("xi-api-key", apiKey())
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "TTS failed: ${response.code} ${response.body?.string()?.take(200)}")
                    return@withContext false
                }

                // Write audio to temp file
                val tempFile = File.createTempFile("verite_tts_", ".mp3", context.cacheDir)
                FileOutputStream(tempFile).use { fos ->
                    response.body?.byteStream()?.copyTo(fos)
                }

                // Play audio on main thread
                withContext(Dispatchers.Main) {
                    playAudioFile(tempFile)
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in TTS speak", e)
            false
        }
    }

    /**
     * Preview a voice by its preview URL (from getAvailableVoices()).
     */
    suspend fun previewVoice(previewUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(previewUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false

                val tempFile = File.createTempFile("verite_preview_", ".mp3", context.cacheDir)
                FileOutputStream(tempFile).use { fos ->
                    response.body?.byteStream()?.copyTo(fos)
                }

                withContext(Dispatchers.Main) {
                    playAudioFile(tempFile)
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error previewing voice", e)
            false
        }
    }

    // ── Speaker Verification (Voice ID / Voice Print) ─────────────────

    /**
     * Enroll a voice sample for speaker identification.
     * Records user audio samples and uploads to ElevenLabs for voice cloning/verification.
     * Returns the cloned voice ID if successful.
     */
    suspend fun enrollVoiceSample(
        audioFile: File,
        userName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", "Verite_${userName}_VoicePrint")
                .addFormDataPart("description", "Voice print for user: $userName")
                .addFormDataPart(
                    "files",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/voices/add")
                .addHeader("xi-api-key", apiKey())
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Voice enrollment failed: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val voiceId = json.optString("voice_id")
                Log.i(TAG, "Voice enrolled successfully: $voiceId")
                voiceId.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enrolling voice", e)
            null
        }
    }

    /**
     * Check API usage / remaining quota.
     */
    suspend fun getUsageInfo(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/user/subscription")
                .addHeader("xi-api-key", apiKey())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val used = json.optInt("character_count", 0)
                val limit = json.optInt("character_limit", 10000)
                Pair(used, limit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting usage info", e)
            null
        }
    }

    // ── Audio Playback ──────────────────────────────────────────────────

    private fun playAudioFile(file: File) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isSpeaking.value = false
                    it.release()
                    file.delete()
                }
                setOnErrorListener { mp, _, _ ->
                    _isSpeaking.value = false
                    mp.release()
                    file.delete()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            _isSpeaking.value = false
            file.delete()
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _isSpeaking.value = false
    }

    fun destroy() {
        stopPlayback()
    }
}
