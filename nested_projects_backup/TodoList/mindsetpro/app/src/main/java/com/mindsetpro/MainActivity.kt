package com.mindsetpro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mindsetpro.ui.MindSetProApp
import com.mindsetpro.ui.theme.MindSetProTheme
import com.mindsetpro.utils.TaskScheduler

class MainActivity : ComponentActivity() {

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Microphone permission granted — voice commands ready
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channels
        TaskScheduler.createNotificationChannels(this)

        // Schedule daily reminders
        TaskScheduler.scheduleDailyHabitReminder(this, hour = 9, minute = 0)
        TaskScheduler.scheduleBedtimeReminder(this, hour = 22, minute = 0)

        // Request microphone permission for voice commands
        requestMicPermission()

        setContent {
            MindSetProTheme {
                MindSetProApp()
            }
        }
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
