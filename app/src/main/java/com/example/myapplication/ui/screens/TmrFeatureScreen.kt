package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.myapplication.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.Background
import com.example.myapplication.ui.theme.NodeBgInactive
import com.example.myapplication.ui.theme.TextMuted
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.outfitFamily
import com.example.myapplication.ui.home.SkyBackground

@Composable
fun TmrFeatureScreen(
    onBackClick: () -> Unit,
    onNavigateToAilmentCategory: () -> Unit,
    onNavigateToSavedReports: () -> Unit
) {
    SkyBackground {
        Scaffold(
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

        // TMR Icon/Image Placeholder
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(
                    brush = Brush.radialGradient(
                colors = listOf(AccentPrimary.copy(alpha = 0.3f), Color.Transparent)
            )
        )
    ) {
        // This would be the sleep mask image
        Surface(
            modifier = Modifier.size(100.dp),
            color = AccentPrimary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                // Image placeholder
            }
        }

        Text(
            text = "TMR Feature",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = outfitFamily
            ),
            color = Color.White,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = Color(0xFF0F1B1A), // Dark pill background
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Text(
                text = "Choose goal here",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Light,
                    fontFamily = outfitFamily
                ),
                color = AccentPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        GoalCard(
            title = "Learning & Memory",
            onClick = { /* Not implemented yet */ }
        )

        Spacer(modifier = Modifier.height(20.dp))

        GoalCard(
            title = "Bad Addiction & Bad habits",
            onClick = onNavigateToAilmentCategory
        )

        Spacer(modifier = Modifier.height(16.dp))

        GoalCard(
            title = "View Saved Reports",
            onClick = onNavigateToSavedReports
        )
    }
}
}
}

@Composable
fun GoalCard(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp) // Adjusted to feel more like the "153px" from mockup
            .clickable(onClick = onClick),
        color = Color(0xFF0F1B1A), // Dark card background
        shape = RoundedCornerShape(35.dp) // Radius-35px
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = outfitFamily,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White
            )
        }
    }
}
