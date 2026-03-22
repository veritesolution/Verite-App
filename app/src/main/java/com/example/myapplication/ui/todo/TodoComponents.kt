package com.example.myapplication.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.Task
import com.example.myapplication.ui.theme.TodoColors

@Composable
fun TaskCard(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = TodoColors.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = TodoColors.accentCyan,
                    uncheckedColor = TodoColors.textSecondary
                )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.task,
                    color = if (task.isCompleted) TodoColors.textSecondary else TodoColors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryBadge(task.category)
                    PriorityBadge(task.priority)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = TodoColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryBadge(category: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = TodoColors.accentPurple.copy(alpha = 0.1f)
    ) {
        Text(
            category,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = TodoColors.accentPurple
        )
    }
}

@Composable
fun PriorityBadge(priority: String) {
    val color = when (priority) {
        "High" -> Color(0xFFFF5252)
        "Medium" -> TodoColors.accentCyan
        else -> TodoColors.textSecondary
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            priority,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = color
        )
    }
}

@Composable
fun SectionHeader(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TodoColors.textSecondary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "($count)",
            fontSize = 12.sp,
            color = TodoColors.textSecondary.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = TodoColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Work") }
    var priority by remember { mutableStateOf("Medium") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TodoColors.surface,
        title = { Text("New Task", color = TodoColors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Task Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                // Simplified selection for brevity
                Text("Category: $category", color = TodoColors.textSecondary)
                Text("Priority: $priority", color = TodoColors.textSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, category, priority) },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
