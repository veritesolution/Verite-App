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
import com.example.myapplication.ui.theme.CardBackground
import com.example.myapplication.ui.theme.DarkBackground
import com.example.myapplication.ui.theme.TealPrimary
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary

@Composable
fun TmrFeatureScreen(
    onBackClick: () -> Unit,
    onNavigateToAddictionCategory: () -> Unit,
    onNavigateToSavedReports: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(id = R.drawable.group_1000006461),
                contentScale = ContentScale.FillBounds
            )
    ) {
        Scaffold(
            topBar = {
                VeriteTopBar(onBackClick = onBackClick)
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        Spacer(modifier = Modifier.height(40.dp))

        // TMR Icon/Image Placeholder
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(TealPrimary.copy(alpha = 0.3f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // This would be the sleep mask image
            Surface(
                modifier = Modifier.size(100.dp),
                color = TealPrimary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                // Image placeholder
            }
        }

        Text(
            text = "TMR Feature",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Choose goal here",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        GoalCard(
            title = "Learning & Memory",
            onClick = { /* Not implemented yet */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        GoalCard(
            title = "Bad Addiction & Bad habits",
            onClick = onNavigateToAddictionCategory
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
            .height(100.dp)
            .clickable(onClick = onClick),
        color = CardBackground,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = TextPrimary
            )
        }
    }
}
