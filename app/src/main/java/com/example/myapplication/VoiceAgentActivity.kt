package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.ui.voice.VoiceAgentSettingsScreen
import com.example.myapplication.utils.ElevenLabsManager
import com.example.myapplication.utils.VoiceIdentityManager

class VoiceAgentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val elevenLabsManager = ElevenLabsManager(this)
        val voiceIdentityManager = VoiceIdentityManager(this)

        setContent {
            VeriteTheme {
                VoiceAgentSettingsScreen(
                    elevenLabsManager = elevenLabsManager,
                    voiceIdentityManager = voiceIdentityManager,
                    onBackClick = { finish() }
                )
            }
        }
    }
}
