/*
 * Brain-Emotion v13.3 — Kotlin Integration Guide
 * ================================================
 * UPDATED for v13.3 API responses (new fields, removed tier0_status)
 */

// ═══ DATA CLASSES (matches v13.3 API responses exactly) ════════════════

data class EmotionState(
    val timestamp: Double,
    val valence: Double,
    val arousal: Double,
    val quadrant: String,
    val valence_confidence: Double,
    val arousal_confidence: Double,
    val basic_emotion: String,
    val basic_emotion_prob: Double,
    val basic_emotion_probs: Map<String, Double>,   // NEW in v13.3
    val signal_quality: Double,
    val faa: Double,                                 // NEW in v13.3
    val smoothed_valence: Double,
    val smoothed_arousal: Double,
    val smoothed_quadrant: String,
    val valence_reliability: String,                 // "MODERATE"
    val arousal_reliability: String,                 // "SPECULATIVE"
    val is_calibrated: Boolean,                      // NEW in v13.3
    val low_quality_warning: Boolean,                // NEW in v13.3
)

data class SoundRecommendation(
    val binaural: BinauralConfig,
    val nature_sounds: List<String>,
    val lofi: LofiConfig,
    val meditation: String?,
    val description: String,
    val should_transition: Boolean,
    val transition_seconds: Double,
    val target_quadrant: String,
    val disclaimer: String,                          // "EXPERIMENTAL: Binaural..."
)

data class BinauralConfig(val beat_hz: Double, val carrier_hz: Double)
data class LofiConfig(val tempo_bpm: Int, val key: String)

data class PsychologistContext(
    val current: Map<String, Any>,
    val trend: String,
    val dominant_5min: String,
    val valence_history: List<Double>,
    val arousal_history: List<Double>,
    val crisis: CrisisInfo,
    val session_seconds: Double,
    val recommendation: String,
    val reliability_note: String,                    // NEW in v13.3
)

data class CrisisInfo(val flag: Boolean, val reason: String?)

// ═══ CALIBRATION API (NEW in v13.3) ════════════════════════════════════

/*
class CalibrationManager(private val apiBaseUrl: String) {
    private val client = OkHttpClient()
    private val gson = Gson()

    /**
     * Send a calibration window during the 60-second baseline recording.
     * Call this every 3 seconds with the latest EEG window.
     */
    suspend fun addCalibrationWindow(f3: FloatArray, f4: FloatArray): Boolean {
        val body = gson.toJson(mapOf("f3" to f3.toList(), "f4" to f4.toList()))
        val request = Request.Builder()
            .url("$apiBaseUrl/api/v1/calibration/add")
            .addHeader("X-API-Key", apiKey)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            val resp = client.newCall(request).execute()
            val json = gson.fromJson(resp.body?.string(), Map::class.java)
            json["status"] == "ok"
        }
    }

    /** Call after the 60-second baseline recording is complete. */
    suspend fun finalizeCalibration(): Boolean {
        val request = Request.Builder()
            .url("$apiBaseUrl/api/v1/calibration/finalize")
            .addHeader("X-API-Key", apiKey)
            .post("".toRequestBody())
            .build()
        return withContext(Dispatchers.IO) {
            val resp = client.newCall(request).execute()
            val json = gson.fromJson(resp.body?.string(), Map::class.java)
            json["is_calibrated"] == true
        }
    }
}
*/

// ═══ PSYCHOLOGIST INTEGRATION (updated for v13.3) ═══════════════════════

/*
class PsychologistEmotionBridge(private val apiBaseUrl: String, private val apiKey: String) {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun getEmotionContext(): PsychologistContext? {
        val request = Request.Builder()
            .url("$apiBaseUrl/api/v1/psychologist/context")
            .addHeader("X-API-Key", apiKey)
            .build()
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                gson.fromJson(response.body?.string(), PsychologistContext::class.java)
            } catch (e: Exception) { null }
        }
    }

    /** Inject into AI psychologist system prompt before each response. */
    fun buildSystemPromptInjection(ctx: PsychologistContext): String {
        return """
        [REAL-TIME EMOTION DATA — ${ctx.reliability_note}]
        Emotion: ${(ctx.current["basic_emotion"] as? String) ?: "unknown"}
        Valence: ${ctx.current["smoothed_valence"]} (${if ((ctx.current["smoothed_valence"] as? Double ?: 0.0) > 0) "positive" else "negative"})
        Arousal: ${ctx.current["smoothed_arousal"]}
        Calibrated: ${ctx.current["is_calibrated"]}
        Signal quality: ${ctx.current["signal_quality"]}
        ${if (ctx.current["low_quality_warning"] == true) "⚠️ LOW SIGNAL QUALITY — electrode contact may be poor" else ""}
        Trend: ${ctx.trend}
        Dominant (5min): ${ctx.dominant_5min}
        ${if (ctx.crisis.flag) "⚠️ CRISIS: ${ctx.crisis.reason}" else ""}

        [RECOMMENDATION] ${ctx.recommendation}

        Adapt your response. If emotion contradicts words, gently explore.
        Do NOT mention the EEG device or technical details to the user.
        """.trimIndent()
    }
}
*/

// ═══ ADAPTIVE SOUND (updated — includes disclaimer handling) ════════════

/*
class AdaptiveSoundController(private val context: Context) {
    fun onSoundRecommendation(sound: SoundRecommendation) {
        if (!sound.should_transition) return

        val fadeMs = (sound.transition_seconds * 1000).toLong()

        // 1. Binaural beats (EXPERIMENTAL — show disclaimer to user once)
        BinauralBeatGenerator.setFrequency(
            carrierHz = sound.binaural.carrier_hz,
            beatHz = sound.binaural.beat_hz,
            fadeMs = fadeMs
        )

        // 2. Nature sounds
        NatureSoundMixer.crossfadeTo(sound.nature_sounds, fadeMs)

        // 3. Lo-fi
        LofiPlayer.setTempo(sound.lofi.tempo_bpm)
        LofiPlayer.setKey(sound.lofi.key)

        // 4. Meditation
        sound.meditation?.let { MeditationGuide.start(it) }
    }
}
*/
