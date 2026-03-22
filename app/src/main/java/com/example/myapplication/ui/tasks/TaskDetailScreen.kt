package com.example.myapplication.ui.tasks

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
import com.example.myapplication.ml.SentimentMoodTracker
import com.example.myapplication.ui.theme.MindSetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: Task,
    onUpdate: (Task) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var editedName by remember { mutableStateOf(task.task) } // Use 'task' field from entity
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = MindSetColors.textSecondary)
            }
            Text("Task Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MindSetColors.text)
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, "Delete", tint = MindSetColors.accentRed)
            }
        }

        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isDone) MindSetColors.accentGreen.copy(alpha = 0.12f) else MindSetColors.surface2,
            onClick = { isDone = !isDone; hasChanges = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isDone, onCheckedChange = { isDone = it; hasChanges = true })
                Spacer(Modifier.width(12.dp))
                Text(if (isDone) "Completed ✨" else "Mark as complete", color = if (isDone) MindSetColors.accentGreen else MindSetColors.text)
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = editedName,
            onValueChange = { editedName = it; hasChanges = true },
            label = { Text("Task name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MindSetColors.accentCyan)
        )

        Spacer(Modifier.height(8.dp))
        Text("Sentiment: ${String.format("%.2f", sentiment)}", fontSize = 11.sp, color = MindSetColors.textMuted)

        Spacer(Modifier.height(20.dp))

        Text("Category", fontSize = 12.sp, color = MindSetColors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Category.entries.forEach { cat ->
                FilterChip(
                    selected = editedCategory == cat.label,
                    onClick = { editedCategory = cat.label; hasChanges = true },
                    label = { Text(cat.label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("Priority", fontSize = 12.sp, color = MindSetColors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Priority.entries.forEach { pri ->
                FilterChip(
                    selected = editedPriority == pri.label,
                    onClick = { editedPriority = pri.label; hasChanges = true },
                    label = { Text(pri.label) }
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = {
                onUpdate(task.copy(task = editedName.trim(), category = editedCategory, priority = editedPriority, done = isDone))
                hasChanges = false
            },
            enabled = hasChanges && editedName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MindSetColors.accentCyan)
        ) {
            Text("Save Changes", color = MindSetColors.background)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Task?") },
            text = { Text("Are you sure you want to delete this task?") },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Delete", color = MindSetColors.accentRed) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
