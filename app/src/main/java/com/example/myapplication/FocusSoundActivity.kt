package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FocusSoundActivity : AppCompatActivity() {

    private var audioManager: BinauralAudioManager? = null
    private var stressDetectionEngine: StressDetectionEngine? = null
    private var bluetoothLeManager: BluetoothLeManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val soundscapeTitle = intent.getStringExtra("SOUNDSCAPE_TITLE") ?: "sunset"

        // Audio init — wrapped in try-catch to prevent crashes
        try {
            audioManager = BinauralAudioManager.getInstance(this)
            audioManager?.playSound(SoundType.FOCUS)
        } catch (e: Exception) {
            android.util.Log.e("FocusSound", "Audio init failed: ${e.message}")
            Toast.makeText(this, "Audio playback unavailable", Toast.LENGTH_SHORT).show()
        }

        // Bio monitoring — optional, wrapped in try-catch
        try {
            stressDetectionEngine = StressDetectionEngine()
            bluetoothLeManager = BluetoothLeManager.getInstance(this)
            observeBioData()
            observeStress()
        } catch (e: Exception) {
            android.util.Log.e("FocusSound", "Bio monitoring init failed: ${e.message}")
        }

        setContent {
            VeriteTheme {
                SkyBackground {
                    FocusSoundScreen(
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
                android.util.Log.e("FocusSound", "Bio data error: ${e.message}")
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
                android.util.Log.e("FocusSound", "Stress monitoring error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { audioManager?.stopSound() } catch (_: Exception) {}
    }
}

@Composable
fun FocusSoundScreen(
    soundscapeTitle: String,
    onBack: () -> Unit,
    player: Player?
) {
    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }

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
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF00BFA5),
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title Section
        Text(
            text = "🎯 Focus Mode",
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
            text = "Binaural Beats: Beta (14–30 Hz)",
            color = Color(0xFF00BFA5),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Glassmorphic Brain Wave Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "🧠 Live Brain Activity Monitor",
                    color = Color(0xFF00BFA5),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                BrainWaveRow(name = "Theta", hzValue = "6.2 Hz", targetProgress = 0.32f)
                BrainWaveRow(name = "Alpha", hzValue = "9.8 Hz", targetProgress = 0.25f)
                BrainWaveRow(name = "Beta", hzValue = "18.4 Hz", targetProgress = 0.78f)
                BrainWaveRow(name = "Delta", hzValue = "1.5 Hz", targetProgress = 0.15f)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Premium Media Controls
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D2533)) // Soft dark teal
                .clickable {
                    if (isPlaying) player?.pause() else player?.play()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00BFA5)),
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

        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
fun BrainWaveRow(name: String, hzValue: String, targetProgress: Float) {
    var currentProgress by remember { mutableFloatStateOf(targetProgress * 0.8f) }
    
    // Simulate live data fluctuations
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
            color = Color(0xFF00BFA5),
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
