package com.example.myapplication.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.myapplication.R

/**
 * Renders the Headband product image at the center of the home screen.
 * Sized to fit naturally within the orbit ring (not circular-clipped).
 */
@Composable
fun HeadbandCenter(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "headband_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        // 240dp gives it a clear rectangular presence inside the 360dp orbit ring
        modifier = modifier.size(width = 240.dp, height = 160.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.headband),
            contentDescription = "Vérité Sleep Band",
            contentScale = ContentScale.Fit,   // keeps original shape, no cropping
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
        )
    }
}
