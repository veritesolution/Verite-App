package com.example.myapplication.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * Reusable sky background composable.
 * Wraps any content with the animated teal sky (stars, comets, nebula)
 * and a deep space gradient derived from #1C9C91.
 */
@Composable
fun SkyBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Page background gradient (no extra ovals/ellipses)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF030C0B),   // near-black teal at top
                            Color(0xFF071A18),   // dark teal midpoint
                            Color(0xFF050F0E)    // near-black at bottom
                        )
                    )
                )
            }
    ) {
        // Animated star field + comets
        StarSparkle(Modifier.fillMaxSize(), starCount = 22)
        // Page content on top
        content()
    }
}
