package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun VeriteTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = AccentPrimary,
        secondary = AccentDark,
        tertiary = AccentBright,
        background = Background,
        surface = Background,
        onPrimary = Color.Black,
        onSecondary = Color.White,
        onTertiary = Color.Black,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
