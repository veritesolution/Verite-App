package com.example.myapplication.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.MoodEntry
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.DarkSlate
import com.example.myapplication.ui.theme.outfitFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun MoodTrackingCenter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).moodEntryDao() }
    
    var currentMood by remember { mutableStateOf<MoodEntry?>(null) }
    var showSelector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val today = LocalDate.now().toString()
        val entry = withContext(Dispatchers.IO) { dao.getForDate(today) }
        currentMood = entry
        if (entry == null) showSelector = true
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
        modifier = modifier
            .size(200.dp)
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
            targetState = showSelector,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
            },
            label = "MoodState"
        ) { isSelecting ->
            if (isSelecting) {
                MoodSelector(
                    onMoodSelected = { sentiment ->
                        coroutineScope.launch {
                            val newEntry = MoodEntry(
                                sentimentScore = sentiment,
                                momentumScore = 50f // default momentum
                            )
                            withContext(Dispatchers.IO) { dao.insert(newEntry) }
                            currentMood = newEntry
                            showSelector = false
                        }
                    }
                )
            } else {
                MoodDisplay(
                    mood = currentMood,
                    onClick = { showSelector = true }
                )
            }
        }
    }
}

@Composable
fun MoodSelector(onMoodSelected: (Float) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "How are you feeling?",
            color = Color.White,
            fontFamily = outfitFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            MoodOption("😢", -1.0f, onMoodSelected)
            MoodOption("😐", 0.0f, onMoodSelected)
            MoodOption("😊", 0.5f, onMoodSelected)
            MoodOption("🤩", 1.0f, onMoodSelected)
        }
    }
}

@Composable
fun MoodOption(emoji: String, value: Float, onClick: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(DarkSlate)
            .border(1.dp, AccentPrimary.copy(alpha = 0.4f), CircleShape)
            .clickable { onClick(value) },
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 18.sp)
    }
}

@Composable
fun MoodDisplay(mood: MoodEntry?, onClick: () -> Unit) {
    val emoji = when {
        mood == null -> "⏱️"
        mood.sentimentScore >= 0.8f -> "🤩"
        mood.sentimentScore >= 0.3f -> "😊"
        mood.sentimentScore >= -0.2f -> "😐"
        mood.sentimentScore >= -0.7f -> "😢"
        else -> "😫"
    }

    val label = when {
        mood == null -> "Pending"
        mood.sentimentScore >= 0.8f -> "Great"
        mood.sentimentScore >= 0.3f -> "Good"
        mood.sentimentScore >= -0.2f -> "Okay"
        mood.sentimentScore >= -0.7f -> "Down"
        else -> "Terrible"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(DarkSlate)
                .border(2.dp, AccentPrimary.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 32.sp
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Today's Mood",
            color = AccentPrimary,
            fontFamily = outfitFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp
        )
        Text(
            text = label.uppercase(),
            color = Color.White,
            fontFamily = outfitFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
    }
}
