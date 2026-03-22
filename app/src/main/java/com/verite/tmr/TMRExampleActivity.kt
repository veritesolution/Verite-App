package com.verite.tmr

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.launch
import java.io.File

/**
 * Example Activity — How to use TMREngine in your app.
 *
 * ── Changes from v4.1 ──────────────────────────────────────────────
 *  - FIX: API keys loaded from EncryptedSharedPreferences, not BuildConfig.
 *         BuildConfig embeds keys in the APK where they can be decompiled.
 *         EncryptedSharedPreferences uses AES-256 + Android Keystore.
 *  - FIX: generateAudioCues now uses applicationContext (prevents Activity leak)
 *  - FIX: generateAudioCues is now a suspend function — no broken callback pattern
 *
 * For a real app, add a settings screen where users enter their own API keys.
 * Keys are stored encrypted on-device. You should NEVER ship keys in the APK.
 */
class TMRExampleActivity : AppCompatActivity() {

    private lateinit var tmrEngine: TMREngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Load API keys from encrypted storage ──────────────────
        // In a real app, provide a UI for users to enter their keys.
        // Keys are stored with AES-256 encryption backed by Android Keystore.
        val (groqKey, geminiKey) = loadApiKeys()

        // Initialize engine with cascade: Groq → Gemini → Local fallback
        tmrEngine = TMREngine(
            groqApiKey = groqKey,
            geminiApiKey = geminiKey
        )

        Log.d("TMR", "Providers: ${tmrEngine.availableProviders.joinToString(" → ")}")

        // ── Example: Run full pipeline ───────────────────────────────
        val textParam = intent.getStringExtra("EXTRA_TEXT") ?: sampleText
        runTMRPipeline(textParam)
    }

    /**
     * Load API keys from EncryptedSharedPreferences.
     *
     * FIX: BuildConfig.GROQ_API_KEY was extractable by decompiling the APK.
     * EncryptedSharedPreferences uses AES-256-SIV for keys and AES-256-GCM
     * for values, backed by the Android Keystore.
     *
     * In production, add a settings screen where the user enters their own keys.
     * First-run flow: prompt for keys → save here → load on subsequent launches.
     */
    private fun loadApiKeys(): Pair<String?, String?> {
        return try {
            val masterKey = MasterKey.Builder(applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                applicationContext,
                "tmr_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val groqKey = prefs.getString("groq_api_key", null)
            val geminiKey = prefs.getString("gemini_api_key", null)

            if (groqKey.isNullOrBlank() && geminiKey.isNullOrBlank()) {
                Log.w("TMR", "No API keys found — only local fallback available. " +
                    "Call saveApiKeys() from your settings UI to store keys.")
            }

            groqKey to geminiKey
        } catch (e: Exception) {
            Log.e("TMR", "Failed to load encrypted keys: ${e.message}", e)
            null to null
        }
    }

    /**
     * Save API keys to encrypted storage. Call this from your settings UI
     * when the user enters their keys.
     */
    fun saveApiKeys(groqKey: String?, geminiKey: String?) {
        try {
            val masterKey = MasterKey.Builder(applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                applicationContext,
                "tmr_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            prefs.edit().apply {
                if (groqKey != null) putString("groq_api_key", groqKey)
                if (geminiKey != null) putString("gemini_api_key", geminiKey)
                apply()
            }

            Log.d("TMR", "API keys saved securely")
        } catch (e: Exception) {
            Log.e("TMR", "Failed to save encrypted keys: ${e.message}", e)
        }
    }

    private fun runTMRPipeline(text: String) {
        lifecycleScope.launch {
            try {
                // ── Full pipeline in one call ─────────────────────────
                val result = tmrEngine.runFullPipeline(text)

                Log.d("TMR", """
                    ═══════════════════════════════════════
                    🚀 TMR SESSION COMPLETE
                    ═══════════════════════════════════════
                      Source    : ${result.sourceChars} chars
                      Provider  : ${result.provider}
                      Concepts  : ${result.concepts.size}
                      Flashcards: ${result.flashcards.size}
                      Quiz Q's  : ${result.quiz.questions.size}
                    ═══════════════════════════════════════
                """.trimIndent())

                // Display concepts
                result.concepts.forEach { c ->
                    Log.d("TMR", "📌 ${c.term}: ${c.definition.take(80)}...")
                }

                // Display flashcards
                result.flashcards.take(3).forEach { card ->
                    Log.d("TMR", "🃏 [${card.type}] Q: ${card.front.take(60)}")
                }

                // Export JSON files (matches Colab zip output)
                val outputDir = File(filesDir, "tmr_output")
                val files = tmrEngine.exportToJson(result, outputDir)
                files.forEach { (name, file) ->
                    Log.d("TMR", "📄 $name → ${file.absolutePath} (${file.length()} bytes)")
                }

                // ── Generate audio cues ───────────────────────────────
                // FIX: Now a suspend function — properly awaits all synthesis.
                // FIX: Uses applicationContext to prevent Activity leak.
                val audioDir = File(filesDir, "tmr_audio")
                val audioFiles = tmrEngine.generateAudioCues(
                    context = applicationContext,  // FIX: was `this@Activity`
                    concepts = result.concepts,
                    outputDir = audioDir,
                    onProgress = { done, total ->
                        Log.d("TMR", "🎵 Audio: $done / $total")
                    }
                )
                Log.d("TMR", "✅ ${audioFiles.size} audio cues saved")

                Toast.makeText(this@TMRExampleActivity,
                    "✅ TMR complete! ${result.concepts.size} concepts, " +
                    "${result.flashcards.size} cards, ${audioFiles.size} cues",
                    Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e("TMR", "Pipeline failed: ${e.message}", e)
                Toast.makeText(this@TMRExampleActivity,
                    "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Option B: Use individual steps ───────────────────────────────
    private fun runStepByStep(text: String) {
        lifecycleScope.launch {
            // Step 1: Just extract concepts
            val concepts = tmrEngine.extractConcepts(text, maxConcepts = 8)

            // Step 2: Generate flashcards from concepts
            val flashcards = tmrEngine.generateFlashcards(concepts)

            // Step 3: Generate quiz
            val quiz = tmrEngine.generateQuiz(concepts, numQuestions = 5)

            // Use results in your UI...
        }
    }

    companion object {
        // Same sample text from the notebook's Cell 7
        val sampleText = """
            Targeted Memory Reactivation (TMR) uses auditory cues during slow-wave 
            sleep to selectively reactivate learned memories. The hippocampus transfers 
            information from short-term to long-term storage during sleep spindles. 
            Theta oscillations (4-8 Hz) are critical for encoding spatial and episodic 
            memories. Delta waves (0.5-2 Hz) dominate NREM Stage 3 and support 
            declarative memory consolidation. Polysomnography monitors sleep stages 
            via EEG, EOG, and EMG signals. The sleep cycle lasts approximately 90 
            minutes cycling through NREM 1-3 and REM. Sleep spindles are 11-16 Hz 
            bursts generated by thalamic reticular neurons. The anterior cingulate 
            cortex integrates reactivated memories into neocortical networks. Synaptic 
            homeostasis theory proposes that sleep downscales synaptic strength 
            globally while preserving important connections.
        """.trimIndent()
    }
}
