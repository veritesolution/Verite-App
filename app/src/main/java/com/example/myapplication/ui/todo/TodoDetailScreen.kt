package com.example.myapplication.ui.todo

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
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.Priority
import com.example.myapplication.data.model.Task
import com.example.myapplication.ui.theme.TodoColors

/**
 * Task Detail & Edit Screen adapted from MindSetPro.
 * Removed sentiment analysis dependency for cleaner migration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailScreen(
    task: Task,
    onUpdate: (Task) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var editedName by remember { mutableStateOf(task.task) }
    var editedCategory by remember { mutableStateOf(task.category) }
    var editedPriority by remember { mutableStateOf(task.priority) }
    var isDone by remember { mutableStateOf(task.isCompleted) }
    var hasChanges by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TodoColors.background)
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
                Icon(Icons.Default.ArrowBack, "Back", tint = TodoColors.textSecondary)
            }
            Text("Task Details", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = TodoColors.text)
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, "Delete", tint = TodoColors.accentRed)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Status Toggle ────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isDone) TodoColors.accentGreen.copy(alpha = 0.12f)
                    else TodoColors.surface2,
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
                        checkedColor = TodoColors.accentGreen,
                        uncheckedColor = TodoColors.textMuted
                    )
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isDone) "Completed ✨" else "Mark as complete",
                    fontSize = 15.sp,
                    color = if (isDone) TodoColors.accentGreen else TodoColors.text,
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
                focusedBorderColor = TodoColors.accentCyan,
                unfocusedBorderColor = TodoColors.surface3,
                focusedLabelColor = TodoColors.accentCyan,
                unfocusedLabelColor = TodoColors.textMuted,
                cursorColor = TodoColors.accentCyan,
                focusedTextColor = TodoColors.text,
                unfocusedTextColor = TodoColors.text
            )
        )

        Spacer(Modifier.height(24.dp))

        // ── Category ─────────────────────────────────────────────────────────
        Text("Category", fontSize = 12.sp, color = TodoColors.textMuted)
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
                        selectedContainerColor = TodoColors.categoryColor(cat.label).copy(alpha = 0.2f),
                        selectedLabelColor = TodoColors.categoryColor(cat.label),
                        containerColor = TodoColors.surface3,
                        labelColor = TodoColors.textMuted
                    )
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Priority ─────────────────────────────────────────────────────────
        Text("Priority", fontSize = 12.sp, color = TodoColors.textMuted)
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
                        selectedContainerColor = TodoColors.priorityColor(pri.label).copy(alpha = 0.2f),
                        selectedLabelColor = TodoColors.priorityColor(pri.label),
                        containerColor = TodoColors.surface3,
                        labelColor = TodoColors.textMuted
                    )
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Save Button ──────────────────────────────────────────────────────
        Button(
            onClick = {
                onUpdate(task.copy(
                    task = editedName.trim(),
                    category = editedCategory,
                    priority = editedPriority,
                    isCompleted = isDone
                ))
                hasChanges = false
            },
            enabled = hasChanges && editedName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TodoColors.accentCyan,
                contentColor = TodoColors.background,
                disabledContainerColor = TodoColors.surface3,
                disabledContentColor = TodoColors.textMuted
            )
        ) {
            Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    // ── Delete Confirmation ──────────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Task?", color = TodoColors.text) },
            text = { Text("\"${task.task}\" will be permanently removed.",
                color = TodoColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = TodoColors.accentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TodoColors.textMuted)
                }
            },
            containerColor = TodoColors.surface2
        )
    }
}
