package com.example.myapplication.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.AccentBright
import com.example.myapplication.ui.theme.AccentPrimary
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─── Data models ────────────────────────────────────────────────────
private data class DotStar(
    val x: Float, val y: Float,
    val radius: Float, val delay: Int, val duration: Int
)

private data class SparkleData(
    val x: Float, val y: Float,
    val size: Float, val delay: Int, val duration: Int
)

private data class CometData(
    val startX: Float, val startY: Float,
    val angle: Float,              // degrees, e.g. 30 = downward-right
    val speed: Int,                // total animation duration ms
    val delay: Int,
    val tailLength: Float,         // in fraction of screen width
    val color: Color
)

// ─── Sparkle star path (4-point) ────────────────────────────────────
private fun buildStarPath() = Path().apply {
    moveTo(10f, 0f);  lineTo(10.8f, 7.5f); lineTo(17f, 3f)
    lineTo(12.5f, 9.2f); lineTo(20f, 10f); lineTo(12.5f, 10.8f)
    lineTo(17f, 17f); lineTo(10.8f, 12.5f); lineTo(10f, 20f)
    lineTo(9.2f, 12.5f); lineTo(3f, 17f);  lineTo(7.5f, 10.8f)
    lineTo(0f, 10f);  lineTo(7.5f, 9.2f);  lineTo(3f, 3f)
    lineTo(9.2f, 7.5f); close()
}

// ─── Main composable ────────────────────────────────────────────────
@Composable
fun StarSparkle(modifier: Modifier, starCount: Int = 18) {
    val starPath = remember { buildStarPath() }

    // 1. Dense dot-star field
    val dotStars = remember {
        List(90) {
            DotStar(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = 0.6f + Random.nextFloat() * 1.8f,
                delay = Random.nextInt(0, 7000),
                duration = Random.nextInt(1500, 4000)
            )
        }
    }

    // 2. Larger sparkle stars (4-point)
    val sparkleStars = remember {
        List(starCount) {
            SparkleData(
                x = 0.06f + Random.nextFloat() * 0.88f,
                y = 0.04f + Random.nextFloat() * 0.92f,
                size = 4f + Random.nextFloat() * 9f,
                delay = Random.nextInt(0, 6000),
                duration = Random.nextInt(1800, 4500)
            )
        }
    }

    // 3. Comets – streaks across the sky
    val comets = remember {
        List(3) {
            CometData(
                startX = Random.nextFloat() * 0.6f,
                startY = Random.nextFloat() * 0.5f,
                angle = 20f + Random.nextFloat() * 30f,
                speed = Random.nextInt(3200, 6000),
                delay = Random.nextInt(0, 10000),
                tailLength = 0.18f + Random.nextFloat() * 0.14f,
                color = if (it % 2 == 0) AccentPrimary else Color(0xFFB2DFDB)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sky")

    // Dot star twinkle phases
    val dotPhases = dotStars.mapIndexed { i, s ->
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(s.duration, s.delay, FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "dot$i"
        )
    }

    // Sparkle phases
    val sparklePhases = sparkleStars.mapIndexed { i, s ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(s.duration, s.delay, FastOutSlowInEasing),
                RepeatMode.Restart
            ),
            label = "sparkle$i"
        )
    }

    // Comet progress (0 -> 1)
    val cometProgress = comets.mapIndexed { i, c ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(c.speed, c.delay, LinearEasing),
                RepeatMode.Restart
            ),
            label = "comet$i"
        )
    }

    // Nebula shimmer
    val nebulaAlpha by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "nebula"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // ── Nebula glow blobs ──────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00BFA5).copy(alpha = nebulaAlpha),
                    Color.Transparent
                ),
                center = Offset(w * 0.25f, h * 0.3f),
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = Offset(w * 0.25f, h * 0.3f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF4A90D9).copy(alpha = nebulaAlpha * 0.6f),
                    Color.Transparent
                ),
                center = Offset(w * 0.78f, h * 0.6f),
                radius = w * 0.38f
            ),
            radius = w * 0.38f,
            center = Offset(w * 0.78f, h * 0.6f)
        )

        // ── Dot stars ──────────────────────────────────────────────
        dotStars.forEachIndexed { i, star ->
            val alpha = dotPhases[i].value.coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.85f),
                radius = star.radius,
                center = Offset(star.x * w, star.y * h)
            )
        }

        // ── Sparkle (4-point) stars ────────────────────────────────
        sparkleStars.forEachIndexed { i, star ->
            val phase = sparklePhases[i].value
            val scale = if (phase < 0.5f) phase * 2.4f else (1f - phase) * 2.4f
            val alpha = (if (phase < 0.5f) phase * 2f else (1f - phase) * 2f) * 0.85f
            val rotation = phase * 45f
            withTransform({
                translate(star.x * w, star.y * h)
                rotate(rotation)
                scale(scale * (star.size / 20f), scale * (star.size / 20f))
            }) {
                drawPath(
                    path = starPath,
                    color = AccentPrimary.copy(alpha = alpha.coerceIn(0f, 1f))
                )
            }
        }

        // ── Comets ────────────────────────────────────────────────
        comets.forEachIndexed { i, comet ->
            val progress = cometProgress[i].value  // 0..1

            // Head position (moves diagonally across screen)
            val angleRad = Math.toRadians(comet.angle.toDouble())
            val travelX = progress * (w * 0.9f)
            val travelY = progress * (w * 0.9f * sin(angleRad).toFloat() / cos(angleRad).toFloat())

            val headX = comet.startX * w + travelX
            val headY = comet.startY * h + travelY

            if (headX > w + 40 || headX < -40) return@forEachIndexed

            val tailLen = comet.tailLength * w
            val tailX = headX - tailLen * cos(angleRad).toFloat()
            val tailY = headY - tailLen * sin(angleRad).toFloat()

            // Fade in / out near edges
            val edgeFade = (progress * 5f).coerceIn(0f, 1f) *
                    ((1f - progress) * 5f).coerceIn(0f, 1f)

            // Draw tail as gradient line
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        comet.color.copy(alpha = 0.15f * edgeFade),
                        comet.color.copy(alpha = 0.5f * edgeFade)
                    ),
                    start = Offset(tailX, tailY),
                    end = Offset(headX, headY)
                ),
                start = Offset(tailX, tailY),
                end = Offset(headX, headY),
                strokeWidth = 2.dp.toPx()
            )

            // Head glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f * edgeFade),
                        comet.color.copy(alpha = 0.4f * edgeFade),
                        Color.Transparent
                    ),
                    center = Offset(headX, headY),
                    radius = 8.dp.toPx()
                ),
                radius = 8.dp.toPx(),
                center = Offset(headX, headY)
            )
        }
    }
}

// ─── Sparkles around active orbital node ────────────────────────────
@Composable
fun ActiveNodeSparkles(modifier: Modifier, nodeX: Float, nodeY: Float) {
    val starPath = remember { buildStarPath() }

    data class SparkleData(
        val angle: Float,
        val distance: Float,
        val size: Float,
        val delay: Int,
        val duration: Int
    )

    val sparklesData = remember {
        List(8) {
            SparkleData(
                angle = Random.nextFloat() * 360f,
                distance = 4f + Random.nextFloat() * 20f,
                size = 3f + Random.nextFloat() * 6f,
                delay = Random.nextInt(0, 1000),
                duration = Random.nextInt(1000, 2200)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "nodeSparkle")

    val sparklePhases = sparklesData.mapIndexed { index, sparkle ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(sparkle.duration, sparkle.delay, FastOutSlowInEasing),
                RepeatMode.Restart
            ),
            label = "NodeSparkle$index"
        )
    }

    Canvas(modifier = modifier) {
        sparklesData.forEachIndexed { index, sparkle ->
            val phase = sparklePhases[index].value
            
            // Outward movement based on phase
            val currentDistance = (sparkle.distance * phase).dp.toPx()
            val rad = Math.toRadians(sparkle.angle.toDouble())
            val offX = (cos(rad) * currentDistance).toFloat()
            val offY = (sin(rad) * currentDistance).toFloat()

            val scale = if (phase < 0.5f) phase * 2.8f else (1f - phase) * 2.8f
            val alpha = (if (phase < 0.5f) phase * 2f else (1f - phase) * 2f)
            
            withTransform({
                translate(nodeX + offX, nodeY + offY)
                rotate(phase * 90f)
                scale(scale * (sparkle.size / 20f), scale * (sparkle.size / 20f))
            }) {
                drawPath(
                    path = starPath,
                    color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
                )
            }
        }
    }
}
