package com.example.myapplication.utils

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * VoiceIdentityManager — Voice enrollment and speaker recognition for Vérité.
 *
 * Enrollment flow (like Apple's "Hey Siri"):
 * 1. User records 3 voice samples with guided prompts
 * 2. System extracts a voice signature (pitch, energy envelope, zero-crossing rate)
 * 3. Signature is stored locally for future comparison
 *
 * Recognition flow:
 * 1. When wake-word detected, capture a short audio snippet
 * 2. Extract features and compare against stored signature
 * 3. If match confidence > threshold → identified user; else → "unknown"
 */
class VoiceIdentityManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceIdentityMgr"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val PREFS_NAME = "verite_voice_identity"
        private const val KEY_ENROLLED = "is_enrolled"
        private const val KEY_SAMPLES_RECORDED = "samples_recorded"
        private const val KEY_AVG_PITCH = "avg_pitch"
        private const val KEY_AVG_ENERGY = "avg_energy"
        private const val KEY_AVG_ZCR = "avg_zcr"
        private const val KEY_ENROLLED_VOICE_ID = "enrolled_voice_id"
        private const val KEY_USER_NAME = "enrolled_user_name"
        private const val MATCH_THRESHOLD = 0.65f
        private const val RECORD_DURATION_MS = 4000L // 4 seconds per sample

        val ENROLLMENT_PROMPTS = listOf(
            "Hey Vérité, good morning",
            "Hey Vérité, start my sleep session",
            "Hey Vérité, show me my progress"
        )
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── State ────────────────────────────────────────────────────────────

    sealed class EnrollmentState {
        object NotEnrolled : EnrollmentState()
        data class InProgress(val step: Int, val prompt: String) : EnrollmentState()
        object Processing : EnrollmentState()
        object Enrolled : EnrollmentState()
        data class Error(val message: String) : EnrollmentState()
    }

    private val _enrollmentState = MutableStateFlow<EnrollmentState>(
        if (isEnrolled) EnrollmentState.Enrolled else EnrollmentState.NotEnrolled
    )
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    val isEnrolled: Boolean
        get() = prefs.getBoolean(KEY_ENROLLED, false)

    val enrolledUserName: String?
        get() = prefs.getString(KEY_USER_NAME, null)

    val enrolledVoiceId: String?
        get() = prefs.getString(KEY_ENROLLED_VOICE_ID, null)

    val samplesRecorded: Int
        get() = prefs.getInt(KEY_SAMPLES_RECORDED, 0)

    // Voice signature (stored features)
    private data class VoiceSignature(
        val avgPitch: Float,
        val avgEnergy: Float,
        val avgZcr: Float
    )

    private fun getStoredSignature(): VoiceSignature? {
        if (!isEnrolled) return null
        return VoiceSignature(
            avgPitch = prefs.getFloat(KEY_AVG_PITCH, 0f),
            avgEnergy = prefs.getFloat(KEY_AVG_ENERGY, 0f),
            avgZcr = prefs.getFloat(KEY_AVG_ZCR, 0f)
        )
    }

    // ── Enrollment ──────────────────────────────────────────────────────

    fun startEnrollment(userName: String) {
        prefs.edit()
            .putString(KEY_USER_NAME, userName)
            .putInt(KEY_SAMPLES_RECORDED, 0)
            .putBoolean(KEY_ENROLLED, false)
            .apply()
        _enrollmentState.value = EnrollmentState.InProgress(
            step = 0,
            prompt = ENROLLMENT_PROMPTS[0]
        )
    }

    /**
     * Records one voice sample for enrollment.
     * Call this for each step (0, 1, 2).
     * Returns the recorded WAV file or null on failure.
     */
    suspend fun recordEnrollmentSample(step: Int): File? = withContext(Dispatchers.IO) {
        if (step < 0 || step >= ENROLLMENT_PROMPTS.size) return@withContext null

        val outputFile = File(context.filesDir, "voice_sample_$step.wav")
        val success = recordAudioToFile(outputFile, RECORD_DURATION_MS)

        if (success) {
            val newCount = step + 1
            prefs.edit().putInt(KEY_SAMPLES_RECORDED, newCount).apply()

            if (newCount < ENROLLMENT_PROMPTS.size) {
                _enrollmentState.value = EnrollmentState.InProgress(
                    step = newCount,
                    prompt = ENROLLMENT_PROMPTS[newCount]
                )
            } else {
                // All samples collected — process
                _enrollmentState.value = EnrollmentState.Processing
                processEnrollment()
            }
            outputFile
        } else {
            _enrollmentState.value = EnrollmentState.Error("Failed to record audio. Check microphone permission.")
            null
        }
    }

    /**
     * Process all 3 samples to create a voice signature.
     */
    private suspend fun processEnrollment() = withContext(Dispatchers.IO) {
        try {
            val signatures = mutableListOf<VoiceSignature>()

            for (i in 0 until ENROLLMENT_PROMPTS.size) {
                val file = File(context.filesDir, "voice_sample_$i.wav")
                if (!file.exists()) {
                    _enrollmentState.value = EnrollmentState.Error("Missing sample $i")
                    return@withContext
                }

                val pcmData = readWavPcm(file)
                if (pcmData.isEmpty()) {
                    _enrollmentState.value = EnrollmentState.Error("Invalid audio in sample $i")
                    return@withContext
                }

                signatures.add(extractFeatures(pcmData))
            }

            // Average the features across all samples
            val avgPitch = signatures.map { it.avgPitch }.average().toFloat()
            val avgEnergy = signatures.map { it.avgEnergy }.average().toFloat()
            val avgZcr = signatures.map { it.avgZcr }.average().toFloat()

            prefs.edit()
                .putFloat(KEY_AVG_PITCH, avgPitch)
                .putFloat(KEY_AVG_ENERGY, avgEnergy)
                .putFloat(KEY_AVG_ZCR, avgZcr)
                .putBoolean(KEY_ENROLLED, true)
                .apply()

            _enrollmentState.value = EnrollmentState.Enrolled
            Log.i(TAG, "Voice enrollment complete. Pitch=$avgPitch, Energy=$avgEnergy, ZCR=$avgZcr")

        } catch (e: Exception) {
            Log.e(TAG, "Enrollment processing failed", e)
            _enrollmentState.value = EnrollmentState.Error("Processing failed: ${e.message}")
        }
    }

    // ── Speaker Verification ────────────────────────────────────────────

    /**
     * Verify if the given audio matches the enrolled user.
     * Returns confidence score (0.0 - 1.0). Above MATCH_THRESHOLD = match.
     */
    suspend fun verifyVoice(audioFile: File): Float = withContext(Dispatchers.IO) {
        val stored = getStoredSignature() ?: return@withContext 0f

        try {
            val pcmData = readWavPcm(audioFile)
            if (pcmData.isEmpty()) return@withContext 0f

            val incoming = extractFeatures(pcmData)

            // Compute similarity as inverse normalized distance
            val pitchDist = abs(stored.avgPitch - incoming.avgPitch) / (stored.avgPitch.coerceAtLeast(1f))
            val energyDist = abs(stored.avgEnergy - incoming.avgEnergy) / (stored.avgEnergy.coerceAtLeast(1f))
            val zcrDist = abs(stored.avgZcr - incoming.avgZcr) / (stored.avgZcr.coerceAtLeast(1f))

            // Weighted similarity (pitch is most discriminative)
            val similarity = 1f - (pitchDist * 0.5f + energyDist * 0.25f + zcrDist * 0.25f).coerceIn(0f, 1f)

            Log.d(TAG, "Voice verification: similarity=$similarity (threshold=$MATCH_THRESHOLD)")
            similarity
        } catch (e: Exception) {
            Log.e(TAG, "Voice verification error", e)
            0f
        }
    }

    fun isMatchingUser(confidence: Float): Boolean = confidence >= MATCH_THRESHOLD

    // ── Quick record for verification (shorter) ────────────────────────

    suspend fun recordForVerification(): File? = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "voice_verify_${System.currentTimeMillis()}.wav")
        val success = recordAudioToFile(outputFile, 2500L) // 2.5 seconds
        if (success) outputFile else null
    }

    // ── Audio Recording ─────────────────────────────────────────────────

    private suspend fun recordAudioToFile(outputFile: File, durationMs: Long): Boolean {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return false
        }

        var recorder: AudioRecord? = null
        return try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return false
            }

            _isRecording.value = true
            recorder.startRecording()

            val allData = mutableListOf<Byte>()
            val buffer = ByteArray(bufferSize)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < durationMs) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        allData.add(buffer[i])
                    }
                }
            }

            recorder.stop()
            _isRecording.value = false

            // Write as WAV file
            writeWavFile(outputFile, allData.toByteArray(), SAMPLE_RATE, 1, 16)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Audio permission denied", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Recording error", e)
            false
        } finally {
            _isRecording.value = false
            recorder?.release()
        }
    }

    // ── Feature Extraction ──────────────────────────────────────────────

    private fun extractFeatures(pcmData: ShortArray): VoiceSignature {
        val energy = computeRmsEnergy(pcmData)
        val zcr = computeZeroCrossingRate(pcmData)
        val pitch = estimatePitch(pcmData, SAMPLE_RATE)

        return VoiceSignature(
            avgPitch = pitch,
            avgEnergy = energy,
            avgZcr = zcr
        )
    }

    private fun computeRmsEnergy(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        val sumSquares = samples.sumOf { it.toLong() * it.toLong() }
        return sqrt(sumSquares.toDouble() / samples.size).toFloat()
    }

    private fun computeZeroCrossingRate(samples: ShortArray): Float {
        if (samples.size < 2) return 0f
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] >= 0 && samples[i] < 0) || (samples[i - 1] < 0 && samples[i] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / samples.size
    }

    /**
     * Simple autocorrelation-based pitch estimation.
     */
    private fun estimatePitch(samples: ShortArray, sampleRate: Int): Float {
        if (samples.size < sampleRate / 50) return 0f // Need at least 20ms of audio

        val minLag = sampleRate / 500  // 500 Hz max pitch
        val maxLag = sampleRate / 80   // 80 Hz min pitch
        val frameSize = minOf(samples.size, sampleRate / 4) // 250ms frame

        var bestLag = minLag
        var bestCorr = -1.0

        for (lag in minLag..minOf(maxLag, frameSize / 2)) {
            var correlation = 0.0
            var count = 0
            for (i in 0 until frameSize - lag) {
                correlation += samples[i].toDouble() * samples[i + lag].toDouble()
                count++
            }
            if (count > 0) {
                correlation /= count
                if (correlation > bestCorr) {
                    bestCorr = correlation
                    bestLag = lag
                }
            }
        }

        return if (bestCorr > 0) sampleRate.toFloat() / bestLag else 0f
    }

    // ── WAV File I/O ────────────────────────────────────────────────────

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val fileSize = 36 + dataSize

        FileOutputStream(file).use { fos ->
            // RIFF header
            fos.write("RIFF".toByteArray())
            fos.write(intToLittleEndian(fileSize))
            fos.write("WAVE".toByteArray())

            // fmt sub-chunk
            fos.write("fmt ".toByteArray())
            fos.write(intToLittleEndian(16)) // Sub-chunk size
            fos.write(shortToLittleEndian(1)) // PCM format
            fos.write(shortToLittleEndian(channels.toShort()))
            fos.write(intToLittleEndian(sampleRate))
            fos.write(intToLittleEndian(byteRate))
            fos.write(shortToLittleEndian(blockAlign.toShort()))
            fos.write(shortToLittleEndian(bitsPerSample.toShort()))

            // data sub-chunk
            fos.write("data".toByteArray())
            fos.write(intToLittleEndian(dataSize))
            fos.write(pcmData)
        }
    }

    private fun readWavPcm(file: File): ShortArray {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                // Skip WAV header (44 bytes)
                if (raf.length() < 44) return shortArrayOf()
                raf.seek(44)

                val dataSize = (raf.length() - 44).toInt()
                val bytes = ByteArray(dataSize)
                raf.readFully(bytes)

                // Convert to short array (little-endian 16-bit)
                val shorts = ShortArray(dataSize / 2)
                for (i in shorts.indices) {
                    shorts[i] = ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort()
                }
                shorts
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading WAV PCM", e)
            shortArrayOf()
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToLittleEndian(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            (value.toInt() shr 8 and 0xFF).toByte()
        )
    }

    // ── Reset ───────────────────────────────────────────────────────────

    fun resetEnrollment() {
        prefs.edit().clear().apply()
        // Delete sample files
        for (i in 0 until ENROLLMENT_PROMPTS.size) {
            File(context.filesDir, "voice_sample_$i.wav").delete()
        }
        _enrollmentState.value = EnrollmentState.NotEnrolled
        Log.i(TAG, "Voice enrollment reset")
    }

    /**
     * Save the ElevenLabs cloned voice ID for this user.
     */
    fun setEnrolledVoiceId(voiceId: String) {
        prefs.edit().putString(KEY_ENROLLED_VOICE_ID, voiceId).apply()
    }
}
