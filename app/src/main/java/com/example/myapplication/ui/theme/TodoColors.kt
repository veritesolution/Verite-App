package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

object TodoColors {
    val background = Color(0xFF060608)
    val surface = Color(0xFF0E0E14)
    val surface2 = Color(0xFF16161F)
    val surface3 = Color(0xFF2A2A3A)
    val text = Color(0xFFF0F0F8)
    val textSecondary = Color(0xFF8888A8)
    val textMuted = Color(0xFF55556A)

    val accentCyan = Color(0xFF00E5FF)
    val accentPurple = Color(0xFFBB86FC)
    val accentGreen = Color(0xFF00E676)
    val accentOrange = Color(0xFFFF9100)
    val accentRed = Color(0xFFFF5252)
    val accentYellow = Color(0xFFFFD740)
    val accentPink = Color(0xFFFF4081)

    // Category colors
    val healthColor = accentGreen
    val workColor = accentCyan
    val learningColor = accentPurple
    val mindfulnessColor = accentYellow
    val personalColor = accentPink
    val financeColor = accentOrange

    // Priority colors
    val highPriority = accentRed
    val mediumPriority = accentOrange
    val lowPriority = accentGreen

    fun categoryColor(category: String): Color = when (category) {
        "Health" -> healthColor
        "Work" -> workColor
        "Learning" -> learningColor
        "Mindfulness" -> mindfulnessColor
        "Personal" -> personalColor
        "Finance" -> financeColor
        else -> accentCyan
    }

    fun priorityColor(priority: String): Color = when (priority) {
        "High" -> highPriority
        "Medium" -> mediumPriority
        "Low" -> lowPriority
        else -> textSecondary
    }
}
