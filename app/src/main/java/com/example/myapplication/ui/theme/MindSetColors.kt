package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

object MindSetColors {
    val background = Color(0xFF050F0E)
    val surface = Color(0xFF091A18)
    val surface2 = Color(0xFF0D2B28)
    val surface3 = Color(0xFF15403C)
    val text = Color(0xFFDCE8E4)
    val textSecondary = Color(0xFFB4D2CD)
    val textMuted = Color(0xFF7AA8A1)

    val accentCyan = Color(0xFF1C9C91)    // Primary Teal
    val accentPurple = Color(0xFF3A8A9E)  // Muted Slate-Teal
    val accentGreen = Color(0xFF2DD4AA)   // Bright Accent
    val accentOrange = Color(0xFFE5A170)  // Muted Coral/Gold
    val accentRed = Color(0xFFD66A6A)     // Muted Red
    val accentYellow = Color(0xFFD4C270)  // Muted Gold
    val accentPink = Color(0xFFB27C9B)    // Muted Plum/Pink

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
