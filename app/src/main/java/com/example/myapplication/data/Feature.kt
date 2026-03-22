package com.example.myapplication.data

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck

data class Feature(
    val id: Int,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val destination: String? = null
)

val featuresList = listOf(
    Feature(0, "TMR", "Targeted Memory Reactivation", Icons.Default.Memory, "com.example.myapplication.TmrFeatureActivity"),
    Feature(1, "Sleep Data", "Real-time sleep analytics", Icons.Default.BarChart, "com.example.myapplication.SleepDataActivity"),
    Feature(2, "Bio Feedback", "Biometric monitoring", Icons.Default.MonitorHeart, "com.example.myapplication.BioFeedbackActivity"),
    Feature(3, "Adaptive Sound", "Focus, Relax, Sleep & Meditate sounds", Icons.Default.AutoAwesome, "com.example.myapplication.AdaptiveSoundActivity"),
    Feature(6, "Diagnostics", "Hardware sensor validation", Icons.Default.Memory, "com.example.myapplication.BioWearableDiagnosticActivity"),
    Feature(4, "Alarm", "Smart wake-up timing", Icons.Default.Alarm, "com.example.myapplication.AlarmActivity"),
    Feature(5, "To-Do List", "Sleep intention setting", Icons.AutoMirrored.Filled.PlaylistAddCheck, "com.example.myapplication.MindSetActivity")
)
