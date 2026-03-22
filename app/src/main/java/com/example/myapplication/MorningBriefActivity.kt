package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.myapplication.ui.morningbrief.MorningBriefScreen
import com.example.myapplication.ui.morningbrief.MorningBriefViewModel
import com.example.myapplication.ui.theme.VeriteTheme

class MorningBriefActivity : ComponentActivity() {
    
    private val viewModel: MorningBriefViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeriteTheme {
                MorningBriefScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onVoiceAssistant = {
                        viewModel.toggleVoiceAssistant()
                    }
                )
            }
        }
    }
}
