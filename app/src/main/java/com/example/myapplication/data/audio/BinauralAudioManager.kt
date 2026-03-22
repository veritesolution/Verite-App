package com.example.myapplication.data.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SoundType {
    FOCUS, RELAX, SLEEP, MEDITATE
}

class BinauralAudioManager private constructor(context: Context) {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
        addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("BinauralAudioManager", "Player error: ${error.message}")
                _isPlaying.value = false
            }
        })
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    val player: Player get() = exoPlayer

    var currentSoundType: SoundType? = null
        private set

    private val soundUrls = mapOf(
        SoundType.FOCUS to "https://verite-life.com/music/Gamma/Memory.mp3",
        SoundType.RELAX to "https://verite-life.com/music/Alhpa/Day%20Dream%20on%20The%20Alpha%20Wave%20-%201hr%20Pure%20Binaural%20Beat%20Session%20at%20(11Hz)%20Intervals.mp3",
        SoundType.SLEEP to "https://verite-life.com/music/Delta/4%20Hz%20Pure%20BINAURAL%20Beats%20%F0%9F%9B%91%20DELTA%20Waves%20%5B100%20Hz%20Base%20Frequency%5D%20%5BlxNVfa2uD7Q%5D.mp3",
        SoundType.MEDITATE to "https://verite-life.com/music/thete/6%20Hz%20-%20Theta%20_%20Pure%20Binaural%20Frequency%20%5BaCF07BQ3znE%5D.mp3"
    )

    companion object {
        @Volatile
        private var INSTANCE: BinauralAudioManager? = null

        fun getInstance(context: Context): BinauralAudioManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BinauralAudioManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun playSound(type: SoundType) {
        if (currentSoundType == type && exoPlayer.isPlaying) return
        
        val url = soundUrls[type] ?: return
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        currentSoundType = type
        _isPlaying.value = true
    }

    fun stopSound() {
        exoPlayer.stop()
        currentSoundType = null
        _isPlaying.value = false
    }

    /**
     * Updates volume based on stress score (0-100).
     * Stress score of 100 will set volume to lower (e.g. 0.3) for calming effect,
     * or higher if intended to overpower.
     * Let's implement it as: higher stress = lower volume (more subtle) to avoid overstimulation.
     */
    fun updateAdaptiveVolume(stressScore: Int) {
        // Map 0-100 stress score to 1.0-0.3 volume
        val volume = 1.0f - (stressScore / 100f) * 0.7f
        exoPlayer.volume = volume.coerceIn(0.1f, 1.0f)
    }

    fun release() {
        exoPlayer.release()
        INSTANCE = null
    }
}
