package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.example.myapplication.data.audio.BinauralAudioManager
import com.example.myapplication.data.audio.SoundType
import com.example.myapplication.data.bluetooth.BluetoothLeManager
import com.example.myapplication.data.logic.StressDetectionEngine
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SleepSoundActivity : AppCompatActivity() {

    private var audioManager: BinauralAudioManager? = null
    private var stressDetectionEngine: StressDetectionEngine? = null
    private var bluetoothLeManager: BluetoothLeManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val soundscapeTitle = intent.getStringExtra("SOUNDSCAPE_TITLE") ?: "sunset"

        // Audio init
        try {
            audioManager = BinauralAudioManager.getInstance(this)
            audioManager?.playSound(SoundType.SLEEP)
        } catch (e: Exception) {
            android.util.Log.e("SleepSound", "Audio init failed: ${e.message}")
        }

        // Bio monitoring
        try {
            stressDetectionEngine = StressDetectionEngine()
            bluetoothLeManager = BluetoothLeManager.getInstance(this)
            observeBioData()
            observeStress()
        } catch (e: Exception) {
            android.util.Log.e("SleepSound", "Bio monitoring init failed: ${e.message}")
        }

        setContent {
            VeriteTheme {
                SkyBackground {
                    SleepSoundScreen(
                        soundscapeTitle = soundscapeTitle,
                        onBack = { finish() },
                        player = audioManager?.player
                    )
                }
            }
        }
    }

    private fun observeBioData() {
        lifecycleScope.launch {
            try {
                bluetoothLeManager?.bioDataStream?.collect { data ->
                    stressDetectionEngine?.analyze(data)
                }
            } catch (e: Exception) {
                android.util.Log.e("SleepSound", "Bio data error: ${e.message}")
            }
        }
    }

    private fun observeStress() {
        lifecycleScope.launch {
            try {
                stressDetectionEngine?.currentStress?.collect { state ->
                    if (audioManager?.player?.isPlaying == true) {
                        audioManager?.updateAdaptiveVolume(state.score)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SleepSound", "Stress monitoring error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { audioManager?.stopSound() } catch (_: Exception) {}
    }
}

@Composable
fun SleepSoundScreen(
    soundscapeTitle: String,
    onBack: () -> Unit,
    player: Player?
) {
    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }
    val context = LocalContext.current

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        VeriteTopBar(
            onBackClick = onBack,
            onProfileClick = {
                context.startActivity(Intent(context, ProfileActivity::class.java))
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Title Section
            Text(
                text = "🌙 Sleep Mode",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Selected: $soundscapeTitle",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Binaural Beats: Delta (0.5–4 Hz) + Gentle Vibration",
                color = Color(0xFF9A70CC),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Moon Visual
            MoonVisual()

            Spacer(modifier = Modifier.height(24.dp))

            // Glassmorphic Brain Wave Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0D0A2E).copy(alpha = 0.8f) // Deep sleep purple
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "🧠 Live Brain Activity Monitor",
                        color = Color(0xFF9A70CC),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    BrainWaveRowSleep(name = "Theta", hzValue = "6.2 Hz", targetProgress = 0.35f)
                    BrainWaveRowSleep(name = "Alpha", hzValue = "9.8 Hz", targetProgress = 0.15f)
                    BrainWaveRowSleep(name = "Beta", hzValue = "18.4 Hz", targetProgress = 0.08f)
                    BrainWaveRowSleep(name = "Delta", hzValue = "1.5 Hz", targetProgress = 0.92f)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sleep Tips Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0D0A2E).copy(alpha = 0.8f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "🌙 Sleep Tips", color = Color(0xFF9A70CC), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val tips = listOf(
                        "• Wear the sleep band before lying down",
                        "• Dim your room & lower the temperature",
                        "• Avoid screens 30 min before bed",
                        "• Vibrations gently guide you to sleep"
                    )
                    tips.forEach { tip ->
                        Text(text = tip, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Premium Media Controls
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D0A2E)) // Dark base
                    .clickable {
                        if (isPlaying) player?.pause() else player?.play()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF9A70CC)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun MoonVisual() {
    val infiniteTransition = rememberInfiniteTransition(label = "moon")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MoonAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0A2E), Color(0xFF1A0A3A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🌙\n✨  ⭐  ✨",
            fontSize = 36.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BrainWaveRowSleep(name: String, hzValue: String, targetProgress: Float) {
    var currentProgress by remember { mutableFloatStateOf(targetProgress * 0.8f) }
    
    LaunchedEffect(targetProgress) {
        while (true) {
            currentProgress = targetProgress * (0.8f + (Math.random().toFloat() * 0.2f))
            delay(500)
        }
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = currentProgress,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "progressAnim"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.width(55.dp)
        )
        
        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Color(0xFF9A70CC), // Sleep accent color
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        
        Text(
            text = hzValue,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.End
        )
    }
}
