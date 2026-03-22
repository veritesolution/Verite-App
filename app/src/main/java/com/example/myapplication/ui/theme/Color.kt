package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand teal: rgba(28, 156, 145, 1) = #1C9C91 ─────────────────────
val SkyTeal       = Color(0xFF1C9C91)
val SkyTealBright = Color(0xFF23BFB3)
val SkyTealDark   = Color(0xFF0D5C57)

val Background    = Color(0xFF050F0E)   // near-black teal for base
val AccentPrimary = Color(0xFF1C9C91)   // brand teal
val AccentBright  = Color(0xFF2DD4AA)   // lighter highlight
val AccentDark    = Color(0xFF0D6B65)
val TextPrimary   = Color(0xFFDCE8E4)
val TextActive    = Color(0xFFC8F5E8)
val AccentSecondary = Color(0xFF1C9C91)
val StatusConnected = Color(0xFF1C9C91)
val DividerColor  = Color(28, 156, 145, (0.15 * 255).toInt())

// Semantic
val TextMuted      = Color(180, 210, 205, (0.65 * 255).toInt())
val TextFaint      = Color(180, 210, 205, (0.4 * 255).toInt())
val TextUltraFaint = Color(180, 210, 205, (0.25 * 255).toInt())

val NodeBgInactive = Color(5, 25, 22, (0.92 * 255).toInt())
val NodeBgActive   = Color(28, 156, 145, (0.12 * 255).toInt())

val BorderInactive = Color(28, 156, 145, (0.12 * 255).toInt())
val BorderActive   = Color(28, 156, 145, (0.55 * 255).toInt())

val ConnectorInactive = Color(28, 156, 145, (0.18 * 255).toInt())
val ConnectorActive   = Color(28, 156, 145, (0.7 * 255).toInt())

val RingTrackInner = Color(28, 156, 145, (0.08 * 255).toInt())
val RingTrackMid   = Color(28, 156, 145, (0.12 * 255).toInt())
val RingTrackOuter = Color(28, 156, 145, (0.06 * 255).toInt())

val AmbientGlow = Color(28, 156, 145, (0.10 * 255).toInt())

val BandGradient = listOf(
    Color(0xFF0D3B38),
    Color(0xFF1C9C91),
    Color(0xFF23BFB3),
    Color(0xFF071A18)
)
