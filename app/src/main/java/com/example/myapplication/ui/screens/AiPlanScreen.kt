package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.*
import com.example.myapplication.viewmodel.AiPlanState
import com.example.myapplication.viewmodel.AiViewModel

@Composable
fun AiPlanScreen(
    viewModel: AiViewModel,
    ailmentType: String,
    frequency: String,
    reasonForAilment: String,
    duration: String,
    reasonForStopping: String,
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        if (uiState is AiPlanState.Idle) {
            viewModel.generatePlan(
                ailmentType,
                frequency,
                reasonForAilment,
                duration,
                reasonForStopping
            )
        }
    }

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
                Surface(
                    color = AccentPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your 21-Day Recovery Plan",
                            fontFamily = outfitFamily,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                when (uiState) {
                    is AiPlanState.Loading -> {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentPrimary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "AI is deep thinking and crafting your personalized plan...",
                                    color = TextMuted,
                                    fontFamily = outfitFamily,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                    is AiPlanState.Success -> {
                            val plan = (uiState as AiPlanState.Success).plan
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                color = NodeBgInactive.copy(alpha = 0.45f), // Frosted glass feel
                                shape = RoundedCornerShape(24.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.4f))
                            ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                Text(
                                    text = plan,
                                    color = Color.White,
                                    fontFamily = outfitFamily,
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }
                    is AiPlanState.Error -> {
                        val message = (uiState as AiPlanState.Error).message
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Oops! Something went wrong.",
                                    color = Color.Red,
                                    fontFamily = outfitFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = message,
                                    color = TextMuted,
                                    fontFamily = outfitFamily,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                                Button(
                                    onClick = { 
                                        viewModel.generatePlan(
                                            ailmentType,
                                            frequency,
                                            reasonForAilment,
                                            duration,
                                            reasonForStopping
                                        ) 
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NodeBgInactive)
                                ) {
                                    Text("Retry", fontFamily = outfitFamily, color = Color.White)
                                }
                            }
                        }
                    }
                    else -> {}
                }

                Button(
                    onClick = onDoneClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    enabled = uiState is AiPlanState.Success,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary,
                        disabledContainerColor = NodeBgInactive
                    ),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Text(
                        text = "I'm ready to start!",
                        color = if (uiState is AiPlanState.Success) Color.Black else TextMuted,
                        fontFamily = outfitFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
