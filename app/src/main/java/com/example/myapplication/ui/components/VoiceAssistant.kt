package com.example.myapplication.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun VoiceAssistantButton(
    onClick: () -> Unit,
    isListening: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Pulsing animation when listening
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(70.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring when listening
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                TealPrimary.copy(alpha = alpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        
        // Main button
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isListening) {
                            listOf(TealPrimary, TealSecondary)
                        } else {
                            listOf(CardBackground, CardBackgroundDark)
                        }
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Assistant",
                tint = if (isListening) Color.White else TealPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun VoiceAssistantOverlay(
    isVisible: Boolean,
    isListening: Boolean,
    recognizedText: String,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onTextCommand: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var manualInput by remember { mutableStateOf("") }
    
    if (isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                // Animated voice waves
                VoiceWaveAnimation(isListening = isListening)
                
                // Status text
                Text(
                    text = when {
                        errorMessage != null -> errorMessage
                        isListening -> "Listening..."
                        else -> "Tap to speak or type below"
                    },
                    fontSize = 20.sp,
                    color = if (errorMessage != null) Color(0xFFFF6B6B) else Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                
                // Help text with suggestions
                if (!isListening && recognizedText.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "Try these commands:",
                            fontSize = 14.sp,
                            color = TealSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        com.example.myapplication.utils.VoiceCommandProcessor.getSuggestions().take(3).forEach { suggestion ->
                            Text(
                                text = "• $suggestion",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                
                // Recognized text
                if (recognizedText.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(horizontal = 24.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = CardBackground
                    ) {
                        Text(
                            text = recognizedText,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                
                // Voice assistant button
                VoiceAssistantButton(
                    onClick = { /* Toggle listening */ },
                    isListening = isListening,
                    modifier = Modifier.padding(top = 16.dp)
                )
                
                // Manual text input
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(top = 16.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = CardBackground
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            placeholder = { 
                                Text(
                                    "Type command here...",
                                    color = TextSecondary
                                ) 
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = TealPrimary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        
                        IconButton(
                            onClick = {
                                if (manualInput.isNotEmpty()) {
                                    onTextCommand(manualInput)
                                    manualInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(TealPrimary, androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveAnimation(
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 20f,
                targetValue = 60f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 800,
                        delayMillis = index * 100,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_$index"
            )
            
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(if (isListening) height.dp else 20.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(TealPrimary, TealSecondary)
                        )
                    )
            )
        }
    }
}

@Composable
fun FloatingVoiceAssistant(
    onVoiceCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val voiceManager = remember { com.example.myapplication.utils.VoiceRecognitionManager(context) }
    
    val isListening by voiceManager.isListening.collectAsState()
    val recognizedText by voiceManager.recognizedText.collectAsState()
    val error by voiceManager.error.collectAsState()
    
    var showOverlay by remember { mutableStateOf(false) }
    
    // Process recognized text
    LaunchedEffect(recognizedText) {
        if (recognizedText.isNotEmpty()) {
            delay(1000) // Show the recognized text briefly
            onVoiceCommand(recognizedText)
            delay(500)
            showOverlay = false
        }
    }
    
    // Handle errors
    LaunchedEffect(error) {
        if (error != null) {
            delay(2000) // Show error briefly
            showOverlay = false
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            voiceManager.destroy()
        }
    }
    
    Box(modifier = modifier) {
        // Floating button
        VoiceAssistantButton(
            onClick = {
                showOverlay = true
                if (!isListening) {
                    voiceManager.startListening()
                } else {
                    voiceManager.stopListening()
                }
            },
            isListening = isListening
        )
        
        // Full screen overlay
        VoiceAssistantOverlay(
            isVisible = showOverlay,
            isListening = isListening,
            recognizedText = recognizedText,
            errorMessage = error,
            onDismiss = {
                showOverlay = false
                voiceManager.stopListening()
            },
            onTextCommand = { command ->
                onVoiceCommand(command)
                showOverlay = false
            }
        )
    }
}
