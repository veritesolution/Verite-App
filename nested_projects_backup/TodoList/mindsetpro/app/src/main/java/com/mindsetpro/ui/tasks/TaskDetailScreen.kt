package com.mindsetpro.ui.tasks

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindsetpro.data.model.Category
import com.mindsetpro.data.model.Priority
import com.mindsetpro.data.model.Task
import com.mindsetpro.ml.SentimentMoodTracker
import com.mindsetpro.ui.theme.MindSetColors

/**
 * Task Detail & Edit Screen.
 * Shows task info, sentiment analysis of the task name,
 * and allows editing category, priority, and status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: Task,
    onUpdate: (Task) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var editedName by remember { mutableStateOf(task.name) }
    var editedCategory by remember { mutableStateOf(task.category) }
    var editedPriority by remember { mutableStateOf(task.priority) }
    var isDone by remember { mutableStateOf(task.done) }
    var hasChanges by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val sentiment = remember(editedName) { SentimentMoodTracker.analyzeText(editedName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MindSetColors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = MindSetColors.textSecondary)
            }
            Text("Task Details", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MindSetColors.text)
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, "Delete", tint = MindSetColors.accentRed)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Status Toggle ────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isDone) MindSetColors.accentGreen.copy(alpha = 0.12f)
                    else MindSetColors.surface2,
            onClick = {
                isDone = !isDone
                hasChanges = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isDone,
                    onCheckedChange = {
                        isDone = it
                        hasChanges = true
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MindSetColors.accentGreen,
                        uncheckedColor = MindSetColors.textMuted
                    )
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isDone) "Completed ✨" else "Mark as complete",
                    fontSize = 15.sp,
                    color = if (isDone) MindSetColors.accentGreen else MindSetColors.text,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Task Name ────────────────────────────────────────────────────────
        OutlinedTextField(
            value = editedName,
            onValueChange = {
                editedName = it
                hasChanges = true
            },
            label = { Text("Task name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MindSetColors.accentCyan,
                unfocusedBorderColor = MindSetColors.surface3,
                focusedLabelColor = MindSetColors.accentCyan,
                unfocusedLabelColor = MindSetColors.textMuted,
                cursorColor = MindSetColors.accentCyan,
                focusedTextColor = MindSetColors.text,
                unfocusedTextColor = MindSetColors.text
            )
        )

        Spacer(Modifier.height(8.dp))

        // Sentiment indicator
        val sentimentEmoji = when {
            sentiment > 0.3f  -> "😊"
            sentiment > 0f    -> "🙂"
            sentiment < -0.3f -> "😟"
            sentiment < 0f    -> "😐"
            else              -> "😶"
        }
        Text(
            "$sentimentEmoji Sentiment: ${String.format("%.2f", sentiment)}",
            fontSize = 11.sp,
            color = MindSetColors.textMuted
        )

        Spacer(Modifier.height(20.dp))

        // ── Category ─────────────────────────────────────────────────────────
        Text("Category", fontSize = 12.sp, color = MindSetColors.textMuted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Category.entries.forEach { cat ->
                val selected = editedCategory == cat.label
                FilterChip(
                    selected = selected,
                    onClick = {
                        editedCategory = cat.label
                        hasChanges = true
                    },
                    label = { Text(cat.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MindSetColors.categoryColor(cat.label).copy(alpha = 0.2f),
                        selectedLabelColor = MindSetColors.categoryColor(cat.label),
                        containerColor = MindSetColors.surface3,
                        labelColor = MindSetColors.textMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = MindSetColors.surface3,
                        selectedBorderColor = MindSetColors.categoryColor(cat.label).copy(alpha = 0.5f),
                        enabled = true,
                        selected = selected
                    ),
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Priority ─────────────────────────────────────────────────────────
        Text("Priority", fontSize = 12.sp, color = MindSetColors.textMuted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { pri ->
                val selected = editedPriority == pri.label
                FilterChip(
                    selected = selected,
                    onClick = {
                        editedPriority = pri.label
                        hasChanges = true
                    },
                    label = { Text(pri.label, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MindSetColors.priorityColor(pri.label).copy(alpha = 0.2f),
                        selectedLabelColor = MindSetColors.priorityColor(pri.label),
                        containerColor = MindSetColors.surface3,
                        labelColor = MindSetColors.textMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = MindSetColors.surface3,
                        selectedBorderColor = MindSetColors.priorityColor(pri.label).copy(alpha = 0.5f),
                        enabled = true,
                        selected = selected
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Metadata ─────────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MindSetColors.surface2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                MetadataRow("ID", task.id)
                MetadataRow("Created", task.createdAt.take(19).replace("T", " "))
                if (task.date != null) MetadataRow("Date", task.date)
                if (task.dueTime != null) MetadataRow("Due", task.dueTime.take(16).replace("T", " "))
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Save Button ──────────────────────────────────────────────────────
        Button(
            onClick = {
                onUpdate(task.copy(
                    name = editedName.trim(),
                    category = editedCategory,
                    priority = editedPriority,
                    done = isDone
                ))
                hasChanges = false
            },
            enabled = hasChanges && editedName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MindSetColors.accentCyan,
                contentColor = MindSetColors.background,
                disabledContainerColor = MindSetColors.surface3,
                disabledContentColor = MindSetColors.textMuted
            )
        ) {
            Text("Save Changes", fontWeight = FontWeight.SemiBold)
        }
    }

    // ── Delete Confirmation ──────────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Task?", color = MindSetColors.text) },
            text = { Text("\"${task.name}\" will be permanently removed.",
                color = MindSetColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MindSetColors.accentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = MindSetColors.textMuted)
                }
            },
            containerColor = MindSetColors.surface2
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = MindSetColors.textMuted)
        Text(value, fontSize = 11.sp, color = MindSetColors.textSecondary)
    }
}
