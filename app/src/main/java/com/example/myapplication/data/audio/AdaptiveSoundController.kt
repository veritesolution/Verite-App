package com.example.myapplication.data.audio

import android.content.Context
import com.example.myapplication.data.logic.SoundRecommendation

class AdaptiveSoundController(private val context: Context) {
    private val audioManager = BinauralAudioManager.getInstance(context)

    fun onSoundRecommendation(sound: SoundRecommendation) {
        if (!sound.should_transition) return

        // 1. Direct Meditation Handling
        if (sound.meditation != null) {
            audioManager.playSound(SoundType.MEDITATE)
            return
        }

        // 2. Map exact binaural frequency recommendations to our existing Headband MP3s
        // (If the backend requests a Delta target, we play the SLEEP profile, etc.)
        when (sound.binaural.beat_hz) {
            in 0.1..4.0 -> {
                // Delta (Deep Sleep)
                audioManager.playSound(SoundType.SLEEP)
            }
            in 4.0..8.0 -> {
                // Theta (Meditation/REM)
                audioManager.playSound(SoundType.MEDITATE)
            }
            in 8.0..14.0 -> {
                // Alpha (Relaxation/Superlearning)
                audioManager.playSound(SoundType.RELAX)
            }
            else -> {
                // Beta/Gamma (Focus)
                audioManager.playSound(SoundType.FOCUS)
            }
        }
    }
}
