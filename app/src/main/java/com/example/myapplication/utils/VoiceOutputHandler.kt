package com.example.myapplication.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Text-to-Speech Handler for providing voice feedback.
 */
class VoiceOutputHandler(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    fun initialize() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isInitialized = true
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    // ── Predefined Responses ─────────────────────────────────────────────────

    fun speakTaskCreated(name: String) = speak("Alright, I've added the task: $name")
    fun speakHabitLogged(name: String) = speak("Great job completing $name!")
    fun speakStreak(name: String, count: Int) = speak("Your current streak for $name is $count days. Keep it up!")
    fun speakError(message: String) = speak("Sorry, I had trouble with that: $message")
}
