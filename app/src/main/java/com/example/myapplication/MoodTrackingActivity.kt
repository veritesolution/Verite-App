package com.example.myapplication

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.MoodEntry
import com.example.myapplication.ui.components.TopBar
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.ui.theme.outfitFamily
import com.example.myapplication.data.logic.EmotionState
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate

class MoodTrackingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeriteTheme {
                MoodTrackingScreen(
                    onBackClick = { finish() },
                    onProfileClick = {
                        startActivity(android.content.Intent(this, ProfileActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
fun MoodTrackingScreen(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    SkyBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(onBackClick = onBackClick, onProfileClick = onProfileClick)
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Live Brain-Emotion Tracker",
                color = Color.White,
                fontSize = 26.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Streaming real-time emotion telemetry directly from your VÉRITÉ Sleep Band...",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(bottom = 50.dp, start = 32.dp, end = 32.dp),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            
            MoodTrackingCenterWidget()
            
            Spacer(modifier = Modifier.weight(1.5f))
        }
    }
}

@Composable
fun MoodTrackingCenterWidget() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).moodEntryDao() }
    
    // Live EEG state
    var liveEmotion by remember { mutableStateOf<EmotionState?>(null) }
    var isPolling by remember { mutableStateOf(true) }
    var savedMood by remember { mutableStateOf<MoodEntry?>(null) }
    
    // Polling Coroutine
    LaunchedEffect(isPolling) {
        val client = OkHttpClient()
        val gson = Gson()
        // Standard Android emulator connection to local host
        val request = Request.Builder().url("http://192.168.1.5:8080/api/v1/emotion").build()
        
        while (isPolling) {
            try {
                withContext(Dispatchers.IO) {
                    val resp = client.newCall(request).execute()
                    val body = resp.body?.string()
                    val emotionData = gson.fromJson(body, EmotionState::class.java)
                    if (emotionData?.basic_emotion != null) {
                        liveEmotion = emotionData
                    }
                }
            } catch (e: Exception) {
                // Ignore timeout/Connection refuse while waiting for server
                liveEmotion = null
            }
            delay(1500) // Poll every 1.5 seconds
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mood_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(320.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentPrimary.copy(alpha = pulseAlpha),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = liveEmotion != null,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
            },
            label = "LiveState"
        ) { isLive ->
            if (isLive) {
                // Live EEG View
                val emotion = liveEmotion!!
                LiveEmotionDisplay(
                    emotion = emotion,
                    onSave = {
                        coroutineScope.launch {
                            val newEntry = MoodEntry(
                                sentimentScore = emotion.smoothed_valence.toFloat(),
                                momentumScore = 50f, 
                                note = "Logged via Brain-Emotion EEG"
                            )
                            withContext(Dispatchers.IO) { dao.insert(newEntry) }
                            savedMood = newEntry
                        }
                    },
                    hasSaved = savedMood != null
                )
            } else {
                // Fallback / Loading
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📡",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Searching for Headband Data...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontFamily = outfitFamily
                    )
                    Text(
                        text = "(Ensure `main.py serve` is running)",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 10.sp,
                        fontFamily = outfitFamily,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LiveEmotionDisplay(emotion: EmotionState, onSave: () -> Unit, hasSaved: Boolean) {
    val basicLabel = emotion.basic_emotion.replace("_", " ").uppercase()
    val isPositive = emotion.smoothed_valence > 0
    val color = if (isPositive) AccentPrimary else Color(0xFFE57373)
    val emoji = when (emotion.basic_emotion.lowercase(java.util.Locale.US)) {
        "happy", "joy" -> "🤩"
        "calm", "relaxed" -> "😌"
        "sad", "depressed" -> "😢"
        "angry", "anxious" -> "😠"
        else -> "🤔"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F1B1A))
                .border(2.dp, color.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 36.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "CURRENT STATE",
            color = color.copy(alpha = 0.8f),
            fontFamily = outfitFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = basicLabel,
            color = Color.White,
            fontFamily = outfitFamily,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VALENCE", fontSize = 10.sp, color = Color.White.copy(alpha=0.5f))
                Text(String.format(java.util.Locale.US, "%.2f", emotion.smoothed_valence), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AROUSAL", fontSize = 10.sp, color = Color.White.copy(alpha=0.5f))
                Text(String.format(java.util.Locale.US, "%.2f", emotion.smoothed_arousal), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("QUALITY", fontSize = 10.sp, color = Color.White.copy(alpha=0.5f))
                Text(String.format(java.util.Locale.US, "%.0f%%", emotion.signal_quality * 100), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(if (hasSaved) Color.White.copy(alpha = 0.2f) else AccentPrimary)
                .clickable(enabled = !hasSaved) { onSave() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (hasSaved) "SAVED TO JOURNAL" else "SAVE MOOD",
                color = if (hasSaved) Color.White.copy(alpha = 0.5f) else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}
