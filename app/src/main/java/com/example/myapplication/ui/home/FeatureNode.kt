package com.example.myapplication.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

@Composable
fun FeatureNode(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val textColor = if (isActive) TextActive else TextMuted
    val iconColor = if (isActive) AccentPrimary else AccentPrimary.copy(alpha = 0.65f)

    if (isActive) {
        // Active node — glowing gradient border + teal-tinted background
        Row(
            modifier = modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00BFA5).copy(alpha = 0.28f),
                            Color(0xFF006E5F).copy(alpha = 0.22f)
                        )
                    ),
                    RoundedCornerShape(12.dp)
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(AccentBright, AccentPrimary, AccentBright)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.W600,
                letterSpacing = 0.05.sp
            )
        }
    } else {
        // Inactive node — visible but quieter
        Row(
            modifier = modifier
                .background(
                    Color(0xFF0A1F1D).copy(alpha = 0.85f),
                    RoundedCornerShape(10.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF00BFA5).copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = iconColor
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.W500,
                letterSpacing = 0.04.sp
            )
        }
    }
}
