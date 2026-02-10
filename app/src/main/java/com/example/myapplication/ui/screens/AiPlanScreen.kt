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
import com.example.myapplication.ui.theme.*
import com.example.myapplication.viewmodel.AiPlanState
import com.example.myapplication.viewmodel.AiViewModel

@Composable
fun AiPlanScreen(
    viewModel: AiViewModel,
    addictionType: String,
    frequency: String,
    reasonForAddiction: String,
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
                addictionType,
                frequency,
                reasonForAddiction,
                duration,
                reasonForStopping
            )
        }
    }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your 21-Day Recovery Plan",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            when (uiState) {
                is AiPlanState.Loading -> {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = TealPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "AI is deep thinking and crafting your personalized plan...",
                                color = TextSecondary,
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
                        color = CardBackground.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = plan,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
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
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = message,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                            Button(
                                onClick = { 
                                    viewModel.generatePlan(
                                        addictionType,
                                        frequency,
                                        reasonForAddiction,
                                        duration,
                                        reasonForStopping
                                    ) 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CardBackground)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDoneClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                enabled = uiState is AiPlanState.Success,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary,
                    disabledContainerColor = CardBackground
                ),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text(
                    text = "I'm ready to start!",
                    color = if (uiState is AiPlanState.Success) DarkBackground else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
}
