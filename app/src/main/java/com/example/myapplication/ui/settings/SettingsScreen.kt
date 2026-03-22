package com.example.myapplication.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MindSetColors
import com.example.myapplication.ui.components.BrandedHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    habitReminderHour: Int,
    bedtimeHour: Int,
    firebaseSyncEnabled: Boolean,
    llmFallbackEnabled: Boolean,
    onHabitReminderChange: (Int) -> Unit,
    onBedtimeChange: (Int) -> Unit,
    onFirebaseSyncToggle: (Boolean) -> Unit,
    onLlmFallbackToggle: (Boolean) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onSeedDemoData: () -> Unit,
    onClearAllData: () -> Unit,
    wakeWordEnabled: Boolean,
    onWakeWordToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MindSetColors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BrandedHeader()

        Spacer(Modifier.height(24.dp))

        SettingsSection("🔔 Notifications")
        SettingsSliderRow(Icons.Default.WbSunny, "Daily Habit Reminder", habitReminderHour, 5..22, { "${it}:00" }, onHabitReminderChange)
        Spacer(Modifier.height(8.dp))
        SettingsSliderRow(Icons.Default.Bedtime, "Bedtime Routine Trigger", bedtimeHour, 19..24, { if (it == 24) "0:00" else "${it}:00" }, onBedtimeChange)

        Spacer(Modifier.height(24.dp))

        SettingsSection("☁️ Data & Sync")
        SettingsToggleRow(Icons.Default.Cloud, "Firebase Cloud Sync", "Sync tasks & habits across devices", firebaseSyncEnabled, onFirebaseSyncToggle)
        Spacer(Modifier.height(8.dp))
        SettingsButtonRow(Icons.Default.Upload, "Export Data", "Save all data as JSON file", onExportData)
        Spacer(Modifier.height(8.dp))
        SettingsButtonRow(Icons.Default.Download, "Import Data", "Restore from JSON backup", onImportData)
        Spacer(Modifier.height(8.dp))
        SettingsButtonRow(Icons.Default.Science, "Load Demo Data", "Seed 90 days of sample data", onSeedDemoData)

        Spacer(Modifier.height(24.dp))

        SettingsSection("🤖 Voice & AI")
        SettingsToggleRow(Icons.Default.SmartToy, "LLM Voice Fallback", "Use Claude when low confidence", llmFallbackEnabled, onLlmFallbackToggle)
        Spacer(Modifier.height(8.dp))
        SettingsToggleRow(Icons.Default.Mic, "Wake-Word Listener", "Listen for 'Hey Verite' in background", wakeWordEnabled, onWakeWordToggle)

        Spacer(Modifier.height(24.dp))

        SettingsSection("⚠️ Danger Zone")
        Surface(shape = RoundedCornerShape(12.dp), color = MindSetColors.accentRed.copy(alpha = 0.1f), onClick = { showClearConfirm = true }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeleteForever, null, tint = MindSetColors.accentRed)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Clear All Data", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MindSetColors.accentRed)
                    Text("Permanently delete everything", fontSize = 11.sp, color = MindSetColors.accentRed.copy(alpha = 0.7f))
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Data?") },
            text = { Text("This will permanently delete all tasks, habits, and history.") },
            confirmButton = { TextButton(onClick = { showClearConfirm = false; onClearAllData() }) { Text("Delete", color = MindSetColors.accentRed) } },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MindSetColors.accentCyan, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun SettingsToggleRow(icon: ImageVector, label: String, description: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MindSetColors.surface2, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MindSetColors.textSecondary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MindSetColors.text)
                Text(description, fontSize = 11.sp, color = MindSetColors.textMuted)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun SettingsButtonRow(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MindSetColors.surface2, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MindSetColors.textSecondary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MindSetColors.text)
                Text(description, fontSize = 11.sp, color = MindSetColors.textMuted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MindSetColors.textMuted)
        }
    }
}

@Composable
fun SettingsSliderRow(icon: ImageVector, label: String, value: Int, range: IntRange, format: (Int) -> String, onValueChange: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MindSetColors.surface2, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MindSetColors.textSecondary)
                Spacer(Modifier.width(14.dp))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MindSetColors.text, modifier = Modifier.weight(1f))
                Text(format(value), fontWeight = FontWeight.Bold, color = MindSetColors.accentCyan)
            }
            Slider(value = value.toFloat(), onValueChange = { onValueChange(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat())
        }
    }
}
