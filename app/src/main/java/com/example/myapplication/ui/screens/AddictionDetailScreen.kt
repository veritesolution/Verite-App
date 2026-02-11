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
import com.example.myapplication.ui.theme.*
import com.example.myapplication.utils.ReportUtils
import com.example.myapplication.viewmodel.DashboardViewModel

@Composable
fun AddictionDetailScreen(
    addictionName: String,
    viewModel: DashboardViewModel,
    onBackClick: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAiPlan: (String, String, String, String, String) -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()

    // Check if there's already an active plan for this addiction
    LaunchedEffect(uiState.activePlan) {
        if (uiState.activePlan?.addictionType == addictionName && uiState.activePlan?.isActive == true) {
            onNavigateToDashboard()
        }
    }
    
    val frequencies = remember(addictionName) {
        when {
            addictionName.contains("Smoking", ignoreCase = true) -> 
                listOf("More than 10", "10 a day", "5 a day", "1 a day")
            addictionName.contains("Alcohol", ignoreCase = true) -> 
                listOf("Daily", "Twice a day", "Weekly", "Twice a week", "Two weeks", "Monthly")
            else -> listOf("Daily", "Weekly", "Twice a week", "Monthly")
        }
    }

    val reasons = listOf("Friends", "Stress", "Affair problem", "Workplace problem", "Personal problem", "Others")
    val durations = listOf("6 months ago", "A year ago", "5 years ago", "Long term ago")

    var selectedFrequency by remember { mutableStateOf(frequencies.first()) }
    var selectedReasonForAddiction by remember { mutableStateOf(reasons.first()) }
    var selectedDuration by remember { mutableStateOf(durations.first()) }
    var reasonForStopping by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            VeriteTopBar(onBackClick = onBackClick)
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {

            Surface(
                color = CardBackground.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Bad Addiction & Bad habits",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = CardBackground,
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
                            .background(TealPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = addictionName.first().toString(),
                            color = TealPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = addictionName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Frequency Section
            Text(
                text = if (addictionName.contains("Smoking", ignoreCase = true)) "Smoking Frequency (Daily)" else "Addiction Frequency",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
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
                        label = { Text(freq, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = TealPrimary,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFrequency == freq,
                            borderColor = Color.Transparent,
                            selectedBorderColor = TealPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Reason for Addiction
            Text(
                text = "What is the reason for this addiction?",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                mainAxisSpacing = 8.dp,
                crossAxisSpacing = 8.dp
            ) {
                reasons.forEach { res ->
                    FilterChip(
                        selected = selectedReasonForAddiction == res,
                        onClick = { selectedReasonForAddiction = res },
                        label = { Text(res, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = TealPrimary,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedReasonForAddiction == res,
                            borderColor = Color.Transparent,
                            selectedBorderColor = TealPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Duration
            Text(
                text = "When did this addiction start?",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
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
                        label = { Text(dur, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = TealPrimary,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedDuration == dur,
                            borderColor = Color.Transparent,
                            selectedBorderColor = TealPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Reason for Stopping
            Text(
                text = "Why do you want to stop?",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            OutlinedTextField(
                value = reasonForStopping,
                onValueChange = { reasonForStopping = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Enter your personal motivation...", fontSize = 14.sp, color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedBorderColor = TealPrimary,
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
                        addictionName,
                        selectedFrequency,
                        selectedReasonForAddiction,
                        selectedDuration,
                        reasonForStopping
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text(
                    text = "Generate 21-Day Plan",
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
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
