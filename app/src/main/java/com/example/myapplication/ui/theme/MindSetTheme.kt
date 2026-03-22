package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MindSetColors.accentCyan,
    secondary = MindSetColors.accentPurple,
    tertiary = MindSetColors.accentGreen,
    background = MindSetColors.background,
    surface = MindSetColors.surface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = MindSetColors.text,
    onSurface = MindSetColors.text
)

@Composable
fun MindSetProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For now, always use dark theme as per MindSet aesthetic
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
