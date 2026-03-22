package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.ui.theme.outfitFamily
import kotlinx.coroutines.delay
import kotlin.math.sin

class AiProcessingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VeriteTheme {
                AiProcessingScreen(
                    onComplete = { navigateToPlan() }
                )
            }
        }
    }

    private fun navigateToPlan() {
        val intent = Intent(this, AiPlanActivity::class.java).apply {
            // Forward all extras from AilmentDetail
            val extras = this@AiProcessingActivity.intent.extras
            if (extras != null) {
                putExtras(extras)
            }
        }
        startActivity(intent)
        finish()
    }
}

@Composable
fun AiProcessingScreen(onComplete: () -> Unit) {
    var currentDay by remember { mutableIntStateOf(1) }
    
    // Animate day progress
    LaunchedEffect(Unit) {
        while (currentDay <= 21) {
            delay(150L) // Fast animation for 21 days
            if (currentDay == 21) {
                delay(800L) // Wait a moment at day 21 for explosive flare
                onComplete()
                break
            }
            currentDay++
        }
    }
    
    val progressRatio = (currentDay - 1) / 20f

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f, // moves the sine wave phase smoothly
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_offset"
    )
    
    val flareScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flare_scale"
    )

    SkyBackground {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            
            // Brain wave animation matching user request
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerY = h / 2
                val centerX = w / 2

                // Map progress to X distance growing to the center
                val drawLength = centerX * progressRatio

                // Left Wave (Getting from left corner to center)
                val pathLeft = Path()
                pathLeft.moveTo(0f, centerY)
                var x1 = 0f
                while (x1 <= drawLength) {
                    val scale = x1 / centerX // Tapers intensity towards center naturally
                    val y = centerY + sin((x1 + waveOffset) * 0.05f) * 60f * (1f - scale)
                    if (x1 == 0f) pathLeft.moveTo(x1, centerY) else pathLeft.lineTo(x1, y)
                    x1 += 5f
                }
                
                // Right Wave (Getting from right corner to center)
                val pathRight = Path()
                val rightStart = w
                pathRight.moveTo(rightStart, centerY)
                var x2 = rightStart
                while (x2 >= w - drawLength) {
                    val dx = w - x2
                    val scale = dx / centerX
                    val y = centerY + sin((dx + waveOffset) * 0.05f) * 60f * (1f - scale) // inverted visual sine
                    if (x2 == rightStart) pathRight.moveTo(x2, centerY) else pathRight.lineTo(x2, y)
                    x2 -= 5f
                }

                val activePulse = if (progressRatio >= 0.95f) flareScale else 1f
                
                // Draw Left Path with glowing gradient
                drawPath(
                    path = pathLeft,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, AccentPrimary, Color.White),
                        startX = 0f, endX = drawLength
                    ),
                    style = Stroke(width = 4.dp.toPx() * activePulse, cap = StrokeCap.Round)
                )

                // Draw Right Path with glowing gradient
                drawPath(
                    path = pathRight,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, AccentPrimary, Color.White),
                        startX = w, endX = w - drawLength
                    ),
                    style = Stroke(width = 4.dp.toPx() * activePulse, cap = StrokeCap.Round)
                )
                
                // If they've met: "connecting together"
                if (progressRatio >= 1f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = 40f * activePulse,
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = AccentPrimary.copy(alpha = 0.4f),
                        radius = 120f * activePulse,
                        center = Offset(centerX, centerY)
                    )
                }
            }
            
            // Logo overlay matching Headband home page styling centrally
            if (progressRatio > 0.3f) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.AutoAwesome,
                    contentDescription = "AI Processing",
                    tint = AccentPrimary,
                    modifier = Modifier.size(120.dp)
                )
            }

            // Header Text Block
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Processing Plan",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Our AI is crafting a personalized 21-day recovery sequence\nbased on your unique profile.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    lineHeight = 20.sp
                )
            }

            // Lower Text Counter matching Home aesthetics
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Optimizing Day $currentDay...",
                    color = AccentPrimary,
                    fontSize = 22.sp,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
