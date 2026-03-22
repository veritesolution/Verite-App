package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.*
import com.example.myapplication.utils.ReportUtils
import com.example.myapplication.viewmodel.DashboardViewModel

@Composable
fun AilmentDetailScreen(
    ailmentName: String,
    viewModel: DashboardViewModel,
    onBackClick: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAiPlan: (String, String, String, String, String) -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()

    // Check if there's already an active plan for this ailment
    LaunchedEffect(uiState.activePlan) {
        if (uiState.activePlan?.ailmentType == ailmentName && uiState.activePlan?.isActive == true) {
            onNavigateToDashboard()
        }
    }
    
    val frequencies = remember(ailmentName) {
        when {
            ailmentName.contains("Smoking", ignoreCase = true) -> 
                listOf("More than 10", "10 a day", "5 a day", "1 a day")
            ailmentName.contains("Alcohol", ignoreCase = true) -> 
                listOf("Daily", "Twice a day", "Weekly", "Twice a week", "Two weeks", "Monthly")
            else -> listOf("Daily", "Weekly", "Twice a week", "Monthly")
        }
    }

    val reasons = listOf("Friends", "Stress", "Affair problem", "Workplace problem", "Personal problem", "Others")
    val durations = listOf("6 months ago", "A year ago", "5 years ago", "Long term ago")

    var selectedFrequency by remember { mutableStateOf(frequencies.first()) }
    var selectedReasonForAilment by remember { mutableStateOf(reasons.first()) }
    var selectedDuration by remember { mutableStateOf(durations.first()) }
    var reasonForStopping by remember { mutableStateOf("") }

    SkyBackground {
        Scaffold(
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {

                Surface(
                    color = AccentPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Ailments & Recovery",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = AccentPrimary,
                        fontFamily = outfitFamily,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }

                // Header Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = NodeBgInactive,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ailmentName.first().toString(),
                                color = AccentPrimary,
                                fontFamily = outfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = ailmentName,
                            fontFamily = outfitFamily,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Frequency Section
                Text(
                    text = if (ailmentName.contains("Smoking", ignoreCase = true)) "Smoking Frequency (Daily)" else "Ailment Frequency",
                    color = Color.White,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    mainAxisSpacing = 8.dp,
                    crossAxisSpacing = 8.dp
                ) {
                    frequencies.forEach { freq ->
                        FilterChip(
                            selected = selectedFrequency == freq,
                            onClick = { selectedFrequency = freq },
                            label = { Text(freq, fontSize = 13.sp, fontFamily = outfitFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = AccentPrimary,
                                labelColor = TextMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedFrequency == freq,
                                borderColor = Color.Transparent,
                                selectedBorderColor = AccentPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Reason for Ailment
                Text(
                    text = "What is the primary trigger or reason?",
                    color = Color.White,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    mainAxisSpacing = 8.dp,
                    crossAxisSpacing = 8.dp
                ) {
                    reasons.forEach { res ->
                        FilterChip(
                            selected = selectedReasonForAilment == res,
                            onClick = { selectedReasonForAilment = res },
                            label = { Text(res, fontSize = 13.sp, fontFamily = outfitFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = AccentPrimary,
                                labelColor = TextMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedReasonForAilment == res,
                                borderColor = Color.Transparent,
                                selectedBorderColor = AccentPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Duration
                Text(
                    text = "Timeline of this ailment",
                    color = Color.White,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    mainAxisSpacing = 8.dp,
                    crossAxisSpacing = 8.dp
                ) {
                    durations.forEach { dur ->
                        FilterChip(
                            selected = selectedDuration == dur,
                            onClick = { selectedDuration = dur },
                            label = { Text(dur, fontSize = 13.sp, fontFamily = outfitFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = AccentPrimary,
                                labelColor = TextMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedDuration == dur,
                                borderColor = Color.Transparent,
                                selectedBorderColor = AccentPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Reason for Stopping
                Text(
                    text = "Why do you want to stop?",
                    color = Color.White,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                OutlinedTextField(
                    value = reasonForStopping,
                    onValueChange = { reasonForStopping = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = outfitFamily, color = Color.White, fontSize = 15.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("Enter your personal motivation...", fontSize = 14.sp, color = TextMuted, fontFamily = outfitFamily) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NodeBgInactive,
                        unfocusedContainerColor = NodeBgInactive,
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (reasonForStopping.isBlank()) {
                            Toast.makeText(context, "Please enter why you want to stop", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        onNavigateToAiPlan(
                            ailmentName,
                            selectedFrequency,
                            selectedReasonForAilment,
                            selectedDuration,
                            reasonForStopping
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Text(
                        text = "Generate 21-Day Plan",
                        color = Color.Black,
                        fontFamily = outfitFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

// Simple FlowRow implementation
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeables.forEach { placeable ->
            if (currentRowWidth + placeable.width + mainAxisSpacing.roundToPx() > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + mainAxisSpacing.roundToPx()
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        val height = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * crossAxisSpacing.roundToPx()
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + mainAxisSpacing.roundToPx()
                }
                y += rowHeight + crossAxisSpacing.roundToPx()
            }
        }
    }
}
