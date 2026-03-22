package com.example.myapplication.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.Feature
import com.example.myapplication.ui.theme.*

@Composable
fun FeatureInfo(
    feature: Feature,
    onEnterClick: (Feature) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEnterClick(feature) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = feature,
            transitionSpec = {
                (fadeIn() + slideInVertically()).togetherWith(fadeOut() + slideOutVertically())
            },
            label = "FeatureInfoAnim"
        ) { targetFeature ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Pill badge
                Row(
                    modifier = Modifier
                        .background(Color(45, 212, 170, (0.06 * 255).toInt()), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(45, 212, 170, (0.12 * 255).toInt()), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = targetFeature.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = AccentPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = targetFeature.label.uppercase(),
                        color = AccentPrimary,
                        fontSize = 10.5.sp,
                        fontFamily = outfitFamily,
                        fontWeight = FontWeight.W600,
                        letterSpacing = 1.47.sp // 0.14em
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = targetFeature.description,
                    color = TextFaint,
                    fontSize = 12.5.sp,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.W300,
                    letterSpacing = 0.25.sp, // 0.02em
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
