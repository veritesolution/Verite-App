package com.mindsetpro.ui.settings

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindsetpro.ui.theme.MindSetColors

/**
 * Settings Screen — App configuration and user preferences.
 *
 * Sections:
 *   • Notifications (habit reminder time, bedtime time, task alerts)
 *   • Data (Firebase sync toggle, export/import, seed demo data)
 *   • Voice (LLM fallback toggle, API key)
 *   • About (version, credits)
 */
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
        // ── Header ───────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = MindSetColors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Text("⚙️ Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MindSetColors.text)
        }

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════════════════════════
        // Notifications
        // ═══════════════════════════════════════════════════════════════
        SettingsSection("🔔 Notifications")

        SettingsSliderRow(
            icon = Icons.Default.WbSunny,
            label = "Daily Habit Reminder",
            value = habitReminderHour,
            range = 5..22,
            formatValue = { "${it}:00" },
            onValueChange = onHabitReminderChange
        )

        Spacer(Modifier.height(8.dp))

        SettingsSliderRow(
            icon = Icons.Default.Bedtime,
            label = "Bedtime Routine Trigger",
            value = bedtimeHour,
            range = 19..24,
            formatValue = { if (it == 24) "0:00" else "${it}:00" },
            onValueChange = onBedtimeChange
        )

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════════════════════════
        // Data & Sync
        // ═══════════════════════════════════════════════════════════════
        SettingsSection("☁️ Data & Sync")

        SettingsToggleRow(
            icon = Icons.Default.Cloud,
            label = "Firebase Cloud Sync",
            description = "Sync tasks & habits across devices",
            checked = firebaseSyncEnabled,
            onToggle = onFirebaseSyncToggle
        )

        Spacer(Modifier.height(8.dp))

        SettingsButtonRow(
            icon = Icons.Default.Upload,
            label = "Export Data",
            description = "Save all data as JSON file",
            onClick = onExportData
        )

        Spacer(Modifier.height(8.dp))

        SettingsButtonRow(
            icon = Icons.Default.Download,
            label = "Import Data",
            description = "Restore from JSON backup",
            onClick = onImportData
        )

        Spacer(Modifier.height(8.dp))

        SettingsButtonRow(
            icon = Icons.Default.Science,
            label = "Load Demo Data",
            description = "Seed 90 days of sample habits & tasks",
            onClick = onSeedDemoData
        )

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════════════════════════
        // Voice & AI
        // ═══════════════════════════════════════════════════════════════
        SettingsSection("🤖 Voice & AI")

        SettingsToggleRow(
            icon = Icons.Default.SmartToy,
            label = "LLM Voice Fallback",
            description = "Use Claude Haiku when regex confidence < 70%",
            checked = llmFallbackEnabled,
            onToggle = onLlmFallbackToggle
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MindSetColors.surface2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Voice Command Examples", fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, color = MindSetColors.text)
                Spacer(Modifier.height(8.dp))
                val examples = listOf(
                    "\"Add task called Review PR priority high\"",
                    "\"I completed meditation\"",
                    "\"What's my streak for Morning Run?\"",
                    "\"Show my tasks\"",
                    "\"Create habit called Yoga category Health\""
                )
                examples.forEach { ex ->
                    Text(ex, fontSize = 11.sp, color = MindSetColors.textMuted,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════════════════════════
        // Danger Zone
        // ═══════════════════════════════════════════════════════════════
        SettingsSection("⚠️ Danger Zone")

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MindSetColors.accentRed.copy(alpha = 0.08f),
            onClick = { showClearConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DeleteForever, null,
                    tint = MindSetColors.accentRed, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Clear All Data", fontSize = 14.sp,
                        fontWeight = FontWeight.Medium, color = MindSetColors.accentRed)
                    Text("Permanently delete all tasks, habits, and history",
                        fontSize = 11.sp, color = MindSetColors.accentRed.copy(alpha = 0.6f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════════════════════════
        // About
        // ═══════════════════════════════════════════════════════════════
        SettingsSection("ℹ️ About")

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MindSetColors.surface2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🧠 MindSet Pro", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = MindSetColors.text)
                Text("AI-Powered Habit & Task Intelligence", fontSize = 12.sp,
                    color = MindSetColors.textMuted)
                Spacer(Modifier.height(8.dp))
                Text("Version 1.0.0", fontSize = 11.sp, color = MindSetColors.textMuted)
                Text("Kotlin • Jetpack Compose • Room • On-Device ML",
                    fontSize = 10.sp, color = MindSetColors.textMuted)
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // ── Clear Data Confirmation ──────────────────────────────────────────
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Data?", color = MindSetColors.text) },
            text = {
                Text("This will permanently delete all tasks, habits, streaks, and mood entries. This cannot be undone.",
                    color = MindSetColors.textSecondary)
            },
            confirmButton = {
                TextButton(onClick = { showClearConfirm = false; onClearAllData() }) {
                    Text("Delete Everything", color = MindSetColors.accentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = MindSetColors.textMuted)
                }
            },
            containerColor = MindSetColors.surface2
        )
    }
}

// ── Reusable Setting Components ──────────────────────────────────────────────

@Composable
fun SettingsSection(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        color = MindSetColors.accentCyan, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MindSetColors.surface2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MindSetColors.textSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = MindSetColors.text)
                Text(description, fontSize = 11.sp, color = MindSetColors.textMuted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MindSetColors.accentCyan,
                    checkedTrackColor = MindSetColors.accentCyan.copy(alpha = 0.3f),
                    uncheckedThumbColor = MindSetColors.textMuted,
                    uncheckedTrackColor = MindSetColors.surface3
                )
            )
        }
    }
}

@Composable
fun SettingsButtonRow(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MindSetColors.surface2,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MindSetColors.textSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = MindSetColors.text)
                Text(description, fontSize = 11.sp, color = MindSetColors.textMuted)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MindSetColors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SettingsSliderRow(
    icon: ImageVector,
    label: String,
    value: Int,
    range: IntRange,
    formatValue: (Int) -> String,
    onValueChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MindSetColors.surface2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MindSetColors.textSecondary,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = MindSetColors.text, modifier = Modifier.weight(1f))
                Text(formatValue(value), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = MindSetColors.accentCyan)
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.last - range.first - 1,
                colors = SliderDefaults.colors(
                    thumbColor = MindSetColors.accentCyan,
                    activeTrackColor = MindSetColors.accentCyan,
                    inactiveTrackColor = MindSetColors.surface3
                )
            )
        }
    }
}
