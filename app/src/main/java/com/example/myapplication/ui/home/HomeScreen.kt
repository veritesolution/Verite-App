package com.example.myapplication.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.featuresList
import com.example.myapplication.ui.components.TopBar
import com.example.myapplication.ui.theme.*
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.TextUltraFaint
import com.example.myapplication.ui.theme.outfitFamily
import com.example.myapplication.ui.theme.cormorantFamily
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.home.OrbitRing
import com.example.myapplication.ui.home.FeatureInfo

@Composable
fun HomeScreen(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onFeatureClick: (com.example.myapplication.data.Feature) -> Unit,
    notificationCount: Int = 0,
    onNotificationClick: () -> Unit = {}
) {
    var activeFeature by remember { mutableStateOf(featuresList[0]) }

    SkyBackground {
        // Content overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(
                onBackClick = onBackClick,
                onProfileClick = onProfileClick,
                notificationCount = notificationCount,
                onNotificationClick = onNotificationClick
            )

            Spacer(modifier = Modifier.weight(1f))

            // --- Orbital Section ---
            OrbitRing(
                onFeatureSelected = { activeFeature = it },
                onFeatureClick = onFeatureClick,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Active Feature Info ---
            FeatureInfo(
                feature = activeFeature,
                onEnterClick = onFeatureClick,
                modifier = Modifier.padding(horizontal = 40.dp)
            )

            Spacer(modifier = Modifier.weight(1.2f))

            // --- Footer Section ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                // Horizontal Divider
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AccentPrimary.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Product Title
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(
                            fontFamily = cormorantFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.W500,
                            fontSize = 22.sp
                        )) {
                            append("V")
                        }
                        withStyle(style = SpanStyle(
                            fontFamily = outfitFamily,
                            fontWeight = FontWeight.W200,
                            fontSize = 22.sp
                        )) {
                            append("érit")
                        }
                        withStyle(style = SpanStyle(
                            fontFamily = cormorantFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.W500,
                            fontSize = 22.sp,
                            color = AccentPrimary
                        )) {
                            append("é")
                        }
                        append(" Sleep Band")
                    },
                    color = Color.White,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.W400
                )
                
                Text(
                    text = "PRECISION SLEEP TECHNOLOGY",
                    color = TextUltraFaint,
                    fontSize = 11.sp,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.W300,
                    letterSpacing = 2.2.sp // 0.2em
                )
            }
        }
    }
}
