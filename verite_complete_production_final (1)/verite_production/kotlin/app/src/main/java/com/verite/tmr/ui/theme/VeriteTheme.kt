package com.verite.tmr.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Brand colors ─────────────────────────────────────────────────────────────

private val VeritePurple = Color(0xFF6C63FF)
private val VeritePurpleDark = Color(0xFF9D97FF)
private val VeriteBlue = Color(0xFF1565C0)
private val VeriteBlueDark = Color(0xFF64B5F6)
private val VeriteGreen = Color(0xFF2E7D32)
private val VeriteGreenDark = Color(0xFF81C784)
private val VeriteRed = Color(0xFFC62828)
private val VeriteRedDark = Color(0xFFEF9A9A)

// ── Color schemes ────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = VeriteBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = VeritePurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E0FF),
    onSecondaryContainer = Color(0xFF1D0160),
    tertiary = VeriteGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F0B8),
    onTertiaryContainer = Color(0xFF002106),
    error = VeriteRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
)

private val DarkColorScheme = darkColorScheme(
    primary = VeriteBlueDark,
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = VeritePurpleDark,
    onSecondary = Color(0xFF31009B),
    secondaryContainer = Color(0xFF4800D4),
    onSecondaryContainer = Color(0xFFE8E0FF),
    tertiary = VeriteGreenDark,
    onTertiary = Color(0xFF003910),
    tertiaryContainer = Color(0xFF005319),
    onTertiaryContainer = Color(0xFFB8F0B8),
    error = VeriteRedDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
)

// ── Sleep stage colors (consistent across light/dark) ────────────────────────

object SleepColors {
    val n3 = Color(0xFF1565C0)
    val n2 = Color(0xFF1976D2)
    val n1 = Color(0xFF42A5F5)
    val rem = Color(0xFF7B1FA2)
    val wake = Color(0xFF757575)
    val deliveryWindow = Color(0xFF2E7D32)
    val deliveryWindowBg = Color(0xFFE8F5E9)
    val deliveryWindowBgDark = Color(0xFF1B3A1F)

    fun forStage(stage: String): Color = when (stage) {
        "N3" -> n3; "N2" -> n2; "N1" -> n1; "REM" -> rem; else -> wake
    }
}

// ── Theme composable ─────────────────────────────────────────────────────────

@Composable
fun VeriteTmrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
