package com.example.myapplication.ui.bedtime

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.BedtimeItem
import com.example.myapplication.ui.dashboard.DashboardViewModel
import com.example.myapplication.ui.theme.MindSetColors
import com.example.myapplication.ui.components.BrandedHeader
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color

@Composable
fun BedtimeRoutineScreen(
    viewModel: DashboardViewModel,
    onBackClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val items by viewModel.bedtimeRoutine.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        com.example.myapplication.ui.components.VeriteTopBar(
            onBackClick = onBackClick,
            onProfileClick = onProfileClick
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(horizontal = 24.dp)
        ) {
            BrandedHeader()

        Spacer(Modifier.height(32.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                BedtimeRow(
                    item = item, 
                    onToggle = { viewModel.toggleBedtimeItem(item.id, !item.isChecked) },
                    onDelete = { viewModel.deleteBedtimeItem(item.id) }
                )
            }

            if (items.all { it.isChecked } && items.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(32.dp))
                    Text("All set! Have a peaceful night.", modifier = Modifier.fillMaxWidth(),
                        color = MindSetColors.accentGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        
        // Add new bedtime item
        var newItemText by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                placeholder = { Text("Add routine step...", color = MindSetColors.textMuted, fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MindSetColors.accentCyan,
                    unfocusedBorderColor = MindSetColors.surface3,
                    focusedTextColor = MindSetColors.text,
                    unfocusedTextColor = MindSetColors.text,
                    cursorColor = MindSetColors.accentCyan
                ),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (newItemText.isNotBlank()) {
                        viewModel.addBedtimeItem(newItemText)
                        newItemText = ""
                    }
                },
                modifier = Modifier.background(MindSetColors.surface2, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item", tint = MindSetColors.accentCyan)
            }
        }
    }
    }
}

@Composable
fun BedtimeRow(item: BedtimeItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 8.dp,
            bottomEnd = 24.dp
        ),
        color = if (item.isChecked) Color(0xFF0D2B28) else Color(0xFF15403C), // Emerald tones
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (item.isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null,
                tint = if (item.isChecked) MindSetColors.accentGreen else Color.White
            )
            Spacer(Modifier.width(16.dp))
            Text(
                item.name,
                modifier = Modifier.weight(1f),
                color = if (item.isChecked) MindSetColors.textSecondary else Color.White,
                fontSize = 16.sp,
                fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.Medium
            )
            IconButton(onClick = onDelete) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Close, 
                    contentDescription = "Delete", 
                    tint = if (item.isChecked) MindSetColors.textSecondary else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
