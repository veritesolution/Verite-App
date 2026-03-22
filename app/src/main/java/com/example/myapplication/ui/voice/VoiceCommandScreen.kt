package com.example.myapplication.ui.voice

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.Intent
import com.example.myapplication.data.model.VoiceCommandResult
import com.example.myapplication.ui.theme.MindSetColors

@Composable
fun VoiceCommandScreen(
    isListening: Boolean,
    partialText: String,
    lastResult: VoiceCommandResult?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onExecuteCommand: (VoiceCommandResult) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MindSetColors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🎤 Voice Commands",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MindSetColors.text
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Say commands like:\n\"Add task called Review PR\"\n\"I completed meditation\"\n\"What's my streak for exercise?\"",
            fontSize = 12.sp,
            color = MindSetColors.textMuted,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(48.dp))

        FloatingActionButton(
            onClick = { if (isListening) onStopListening() else onStartListening() },
            modifier = Modifier
                .size(96.dp)
                .then(if (isListening) Modifier.scale(pulseScale) else Modifier),
            shape = CircleShape,
            containerColor = if (isListening) MindSetColors.accentRed else MindSetColors.accentCyan
        ) {
            Icon(
                if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (isListening) "Stop" else "Start",
                modifier = Modifier.size(40.dp),
                tint = MindSetColors.background
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            if (isListening) "Listening..." else "Tap to speak",
            fontSize = 13.sp,
            color = if (isListening) MindSetColors.accentCyan else MindSetColors.textMuted
        )

        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(visible = partialText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MindSetColors.surface2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Heard:", fontSize = 11.sp, color = MindSetColors.textMuted)
                    Text(partialText, fontSize = 16.sp, color = MindSetColors.text,
                        fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(visible = lastResult != null) {
            lastResult?.let { result ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MindSetColors.surface2,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val emoji = when (result.intent) {
                                Intent.ADD_TASK -> "📝"
                                Intent.COMPLETE_TASK -> "✅"
                                Intent.DELETE_TASK -> "🗑️"
                                Intent.ADD_HABIT -> "🎯"
                                Intent.TOGGLE_HABIT -> "🔄"
                                Intent.QUERY_STREAK -> "🔥"
                                Intent.QUERY_TASKS -> "📋"
                                Intent.LIST_HABITS -> "📊"
                                Intent.UNKNOWN -> "❓"
                            }
                            Text(emoji, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    result.intent.name.replace("_", " "),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MindSetColors.accentCyan
                                )
                                Text(
                                    "Confidence: ${(result.confidence * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = MindSetColors.textMuted
                                )
                            }
                        }

                        if (result.entityName != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Target: ${result.entityName}", fontSize = 13.sp, color = MindSetColors.text)
                        }

                        if (result.intent != Intent.UNKNOWN) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onExecuteCommand(result) },
                                colors = ButtonDefaults.buttonColors(containerColor = MindSetColors.accentGreen)
                            ) {
                                Text("Execute", color = MindSetColors.background)
                            }
                        }
                    }
                }
            }
        }
    }
}
