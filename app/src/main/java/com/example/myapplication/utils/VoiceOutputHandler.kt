package com.example.myapplication.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Text-to-Speech Handler for providing voice feedback.
 * Uses Android's built-in TTS engine with configurable speech rate and pitch.
 */
class VoiceOutputHandler(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceOutputHandler"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val settingsManager = SettingsManager(context)
    private var utteranceId = 0

    fun initialize() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            isInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!isInitialized) {
                // Fallback to English
                tts?.setLanguage(Locale.US)
                isInitialized = true
            }
            applySettings()
            Log.i(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    private fun applySettings() {
        tts?.setSpeechRate(settingsManager.ttsSpeechRate)
        tts?.setPitch(settingsManager.ttsPitch)
    }

    fun speak(text: String) {
        if (isInitialized) {
            applySettings() // Re-apply in case user changed settings
            val id = "verite_utterance_${utteranceId++}"
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        } else {
            Log.w(TAG, "TTS not initialized, cannot speak: $text")
        }
    }

    fun speakQueued(text: String) {
        if (isInitialized) {
            applySettings()
            val id = "verite_utterance_${utteranceId++}"
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    val isSpeaking: Boolean
        get() = tts?.isSpeaking == true

    fun stop() {
        tts?.stop()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    // ── Predefined Responses ─────────────────────────────────────────────────

    fun speakTaskCreated(name: String) = speak("Alright, I've added the task: $name")
    fun speakHabitLogged(name: String) = speak("Great job completing $name!")
    fun speakStreak(name: String, count: Int) = speak("Your current streak for $name is $count days. Keep it up!")
    fun speakError(message: String) = speak("Sorry, I had trouble with that: $message")
}
