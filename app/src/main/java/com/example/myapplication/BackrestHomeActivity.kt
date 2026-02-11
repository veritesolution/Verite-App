package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.myapplication.ui.screens.DeviceDetailScreen
import com.example.myapplication.ui.theme.VeriteTheme

class BackrestHomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeriteTheme {
                DeviceDetailScreen(
                    deviceName = "Vérité Backrest",
                    onBackClick = { finish() },
                    onNavigateToAutoPowerOff = {
                        // For now, these can be empty or navigate to relevant activities if they exist
                    },
                    onNavigateToHelpFeedback = {
                        // For now, these can be empty or navigate to relevant activities if they exist
                    }
                )
            }
        }
    }
}
