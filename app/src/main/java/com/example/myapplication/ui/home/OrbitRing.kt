package com.example.myapplication.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Feature
import com.example.myapplication.data.featuresList
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitRing(
    onFeatureSelected: (Feature) -> Unit,
    onFeatureClick: (Feature) -> Unit,
    modifier: Modifier = Modifier
) {
    var isUserDragging by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableIntStateOf(0) }
    val orbitAngle = remember { Animatable(0f) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var idleResumeJob by remember { mutableStateOf<Job?>(null) }

    // ── Haptic feedback when activeIndex changes ─────────────────────
    LaunchedEffect(activeIndex) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onFeatureSelected(featuresList[activeIndex])
    }

    // ── Auto-rotation (runs when user is NOT dragging) ──────────────
    LaunchedEffect(isUserDragging) {
        if (!isUserDragging) {
            orbitAngle.animateTo(
                targetValue = orbitAngle.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(83000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            orbitAngle.stop()
        }
    }

    // ── Auto-highlight cycle (runs when user is NOT dragging) ───────
    LaunchedEffect(isUserDragging) {
        if (!isUserDragging) {
            while (true) {
                delay(3000)
                activeIndex = (activeIndex + 1) % featuresList.size
            }
        }
    }

    // Helper: schedule auto-resume after idle
    fun scheduleAutoResume() {
        idleResumeJob?.cancel()
        idleResumeJob = coroutineScope.launch {
            delay(3000) // 3 seconds idle → resume auto-rotation
            isUserDragging = false
        }
    }

    // Pulse animation for active connector
    val infiniteTransition = rememberInfiniteTransition(label = "orbit_pulse")
    val activePulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "active_pulse"
    )
    val dotPulse by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dot_pulse"
    )

    Box(
        modifier = modifier
            .size(360.dp)
            // ── Manual drag-to-rotate ────────────────────────────────
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isUserDragging = true
                        idleResumeJob?.cancel()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val pos = change.position

                        // Calculate angle delta from drag relative to center
                        val prevX = pos.x - dragAmount.x - centerX
                        val prevY = pos.y - dragAmount.y - centerY
                        val currX = pos.x - centerX
                        val currY = pos.y - centerY

                        val prevAngle = Math.toDegrees(atan2(prevY.toDouble(), prevX.toDouble()))
                        val currAngle = Math.toDegrees(atan2(currY.toDouble(), currX.toDouble()))
                        var delta = (currAngle - prevAngle).toFloat()

                        // Normalize to [-180, 180]
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f

                        coroutineScope.launch {
                            orbitAngle.snapTo(orbitAngle.value + delta)
                        }

                        // Update active feature based on which node is closest to top
                        val sectorSize = 360f / featuresList.size
                        val normalizedAngle = ((orbitAngle.value % 360f) + 360f) % 360f
                        val newIndex = (((-normalizedAngle + 360f) % 360f) / sectorSize).toInt() % featuresList.size
                        if (newIndex != activeIndex) {
                            activeIndex = newIndex
                        }
                    },
                    onDragEnd = {
                        scheduleAutoResume()
                    },
                    onDragCancel = {
                        scheduleAutoResume()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // --- Orbit Tracks ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2

            // Inner ring (stroke only — no fill)
            drawCircle(
                color = RingTrackInner,
                radius = 60.dp.toPx(),
                style = Stroke(1.dp.toPx())
            )

            // Mid orbit track (stroke only — no fill)
            drawCircle(
                color = RingTrackMid,
                radius = 148.dp.toPx(),
                style = Stroke(1.dp.toPx())
            )

            // Outer track
            drawCircle(
                color = RingTrackOuter,
                radius = 170.dp.toPx(),
                style = Stroke(0.8.dp.toPx())
            )

            // --- Connector Lines ---
            featuresList.forEachIndexed { index, _ ->
                val baseAngle = (360f / featuresList.size.toFloat()) * index - 90f
                val totalAngle = baseAngle + orbitAngle.value
                val radians = Math.toRadians(totalAngle.toDouble())

                val startR = 60.dp.toPx()
                val endR = 155.dp.toPx()
                val isActive = index == activeIndex

                val startOffset = Offset(
                    (centerX + cos(radians) * startR).toFloat(),
                    (centerY + sin(radians) * startR).toFloat()
                )
                val endOffset = Offset(
                    (centerX + cos(radians) * endR).toFloat(),
                    (centerY + sin(radians) * endR).toFloat()
                )

                if (isActive) {
                    // Extra glow behind the line
                    drawLine(
                        color = AccentPrimary.copy(alpha = 0.15f * activePulse),
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Glowing gradient line for active connection
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                AccentPrimary.copy(alpha = 0.4f * activePulse),
                                AccentBright.copy(alpha = activePulse),
                                AccentPrimary.copy(alpha = activePulse * 0.9f)
                            ),
                            start = startOffset,
                            end = endOffset
                        ),
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Glowing dot traveling midway
                    val dotR = 94.dp.toPx()
                    val dotCenter = Offset(
                        (centerX + cos(radians) * dotR).toFloat(),
                        (centerY + sin(radians) * dotR).toFloat()
                    )
                    // Outer glow
                    drawCircle(
                        color = AccentPrimary.copy(alpha = 0.25f * activePulse),
                        radius = dotPulse + 3f,
                        center = dotCenter
                    )
                    // Core dot
                    drawCircle(
                        color = Color.White,
                        radius = dotPulse * 0.6f,
                        center = dotCenter
                    )
                } else {
                    // Inactive lines — still visible but subtle
                    drawLine(
                        color = ConnectorInactive,
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = 1.dp.toPx()
                    )
                    // Small static dot at midpoint for inactive nodes
                    val dotR = 94.dp.toPx()
                    drawCircle(
                        color = Color(0xFF00BFA5).copy(alpha = 0.25f),
                        radius = 2.dp.toPx(),
                        center = Offset(
                            (centerX + cos(radians) * dotR).toFloat(),
                            (centerY + sin(radians) * dotR).toFloat()
                        )
                    )
                }
            }
        }

        // --- Center Illustration ---
        HeadbandCenter()

        // --- Feature Nodes ---
        featuresList.forEachIndexed { index, feature ->
            val baseAngle = (360f / featuresList.size.toFloat()) * index - 90f
            val totalAngle = baseAngle + orbitAngle.value
            val radians = Math.toRadians(totalAngle.toDouble())
            val radiusPx = with(density) { 165.dp.toPx() }

            val isActive = index == activeIndex

            val offsetX = (cos(radians) * radiusPx).toFloat()
            val offsetY = (sin(radians) * radiusPx).toFloat()

            if (isActive) {
                val absSparkleX = offsetX + with(density) { 180.dp.toPx() }
                val absSparkleY = offsetY + with(density) { 180.dp.toPx() }
                ActiveNodeSparkles(Modifier.fillMaxSize(), absSparkleX, absSparkleY)
            }

            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { offsetX.toDp() },
                        y = with(density) { offsetY.toDp() }
                    )
                    .pointerInput(feature) {
                        detectTapGestures {
                            isUserDragging = true
                            idleResumeJob?.cancel()
                            activeIndex = index
                            onFeatureSelected(feature)
                            onFeatureClick(feature)
                            scheduleAutoResume()
                        }
                    }
            ) {
                FeatureNode(
                    label = feature.label,
                    icon = feature.icon,
                    isActive = isActive
                )
            }
        }
    }
}
