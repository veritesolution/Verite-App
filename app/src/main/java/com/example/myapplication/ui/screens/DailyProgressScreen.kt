package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.theme.CardBackground
import com.example.myapplication.ui.theme.TealPrimary
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.utils.TextCleaningUtils

@Composable
fun DailyProgressScreen(
    addictionType: String,
    currentDay: Int,
    dailyFocusMinutes: Int,
    completedFocusMinutes: Int,
    aiSuggestion: String,
    onBackClick: () -> Unit,
    onViewTaskClick: () -> Unit,
    onStartFocusSession: () -> Unit
) {
    Scaffold(
        topBar = {
            VeriteTopBar(onBackClick = onBackClick)
        },
        containerColor = Color.Black // Assuming dark theme background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Day $currentDay",
                style = MaterialTheme.typography.displayMedium,
                color = TealPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Recovery: $addictionType",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            // Progress Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Focus Goals",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    CircularProgressIndicator(
                        progress = { 
                            if (dailyFocusMinutes > 0) completedFocusMinutes.toFloat() / dailyFocusMinutes.toFloat() else 0f
                        },
                        modifier = Modifier.size(120.dp),
                        color = TealPrimary,
                        trackColor = Color.Gray.copy(alpha = 0.3f),
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "${TextCleaningUtils.formatTime(completedFocusMinutes)} / ${TextCleaningUtils.formatTime(dailyFocusMinutes)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onStartFocusSession,
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Focus Session", color = Color.Black)
                    }
                }
            }

            // AI Suggestion Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Today's Guidance",
                        style = MaterialTheme.typography.titleLarge,
                        color = TealPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = aiSuggestion,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        lineHeight = 24.sp
                    )
                }
            }

            // Task Button
            Button(
                onClick = onViewTaskClick,
                colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Detailed Tasks", color = TealPrimary, fontSize = 16.sp)
            }
        }
    }
}
