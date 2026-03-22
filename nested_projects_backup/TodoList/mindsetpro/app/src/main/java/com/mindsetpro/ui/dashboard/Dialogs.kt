package com.mindsetpro.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mindsetpro.data.model.Category
import com.mindsetpro.data.model.Priority
import com.mindsetpro.ui.theme.MindSetColors

// ═══════════════════════════════════════════════════════════════════════════════
// Add Task Dialog
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, priority: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Category.WORK) }
    var selectedPriority by remember { mutableStateOf(Priority.MEDIUM) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MindSetColors.surface2,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "📝 New Task",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MindSetColors.text
                )

                Spacer(Modifier.height(20.dp))

                // Task Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Task name") },
                    placeholder = { Text("e.g. Review PR #42") },
                    singleLine = true,
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

                Spacer(Modifier.height(16.dp))

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MindSetColors.accentCyan,
                            unfocusedBorderColor = MindSetColors.surface3,
                            focusedLabelColor = MindSetColors.accentCyan,
                            unfocusedLabelColor = MindSetColors.textMuted,
                            focusedTextColor = MindSetColors.text,
                            unfocusedTextColor = MindSetColors.text
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        Category.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label, color = MindSetColors.text) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .padding(0.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MindSetColors.categoryColor(cat.label),
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Priority Selector (chips)
                Text("Priority", fontSize = 12.sp, color = MindSetColors.textMuted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { pri ->
                        val selected = selectedPriority == pri
                        FilterChip(
                            selected = selected,
                            onClick = { selectedPriority = pri },
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

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MindSetColors.textMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(name.trim(), selectedCategory.label, selectedPriority.label)
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MindSetColors.accentCyan,
                            contentColor = MindSetColors.background
                        )
                    ) {
                        Text("Add Task")
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Add Habit Dialog
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String, category: String, targetDays: List<Int>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎯") }
    var selectedCategory by remember { mutableStateOf(Category.HEALTH) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }

    val dayLabels = listOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)
    val emojiOptions = listOf("🎯", "🏃", "🧘", "📚", "💻", "💧", "🎸", "💰", "🧹", "📝", "🏋️", "🍎", "💊", "🚫", "✍️")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MindSetColors.surface2,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "🎯 New Habit",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MindSetColors.text
                )

                Spacer(Modifier.height(20.dp))

                // Emoji Picker Row
                Text("Icon", fontSize = 12.sp, color = MindSetColors.textMuted)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    emojiOptions.take(10).forEach { e ->
                        val selected = emoji == e
                        Surface(
                            onClick = { emoji = e },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MindSetColors.accentCyan.copy(alpha = 0.2f)
                                    else MindSetColors.surface3,
                            border = if (selected)
                                androidx.compose.foundation.BorderStroke(1.dp, MindSetColors.accentCyan)
                            else null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(e, fontSize = 18.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Habit Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit name") },
                    placeholder = { Text("e.g. Morning Run") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MindSetColors.accentGreen,
                        unfocusedBorderColor = MindSetColors.surface3,
                        focusedLabelColor = MindSetColors.accentGreen,
                        unfocusedLabelColor = MindSetColors.textMuted,
                        cursorColor = MindSetColors.accentGreen,
                        focusedTextColor = MindSetColors.text,
                        unfocusedTextColor = MindSetColors.text
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MindSetColors.accentGreen,
                            unfocusedBorderColor = MindSetColors.surface3,
                            focusedLabelColor = MindSetColors.accentGreen,
                            unfocusedLabelColor = MindSetColors.textMuted,
                            focusedTextColor = MindSetColors.text,
                            unfocusedTextColor = MindSetColors.text
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        Category.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label, color = MindSetColors.text) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Target Days (day-of-week chips)
                Text("Active Days", fontSize = 12.sp, color = MindSetColors.textMuted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEach { (label, dayNum) ->
                        val selected = dayNum in selectedDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedDays = if (selected) selectedDays - dayNum
                                              else selectedDays + dayNum
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MindSetColors.accentGreen.copy(alpha = 0.2f),
                                selectedLabelColor = MindSetColors.accentGreen,
                                containerColor = MindSetColors.surface3,
                                labelColor = MindSetColors.textMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = MindSetColors.surface3,
                                selectedBorderColor = MindSetColors.accentGreen.copy(alpha = 0.5f),
                                enabled = true,
                                selected = selected
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                // Quick presets
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { selectedDays = setOf(1, 2, 3, 4, 5, 6, 7) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Every day", fontSize = 11.sp, color = MindSetColors.accentCyan)
                    }
                    TextButton(
                        onClick = { selectedDays = setOf(1, 2, 3, 4, 5) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Weekdays", fontSize = 11.sp, color = MindSetColors.accentCyan)
                    }
                    TextButton(
                        onClick = { selectedDays = setOf(6, 7) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Weekends", fontSize = 11.sp, color = MindSetColors.accentCyan)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MindSetColors.textMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && selectedDays.isNotEmpty()) {
                                onConfirm(
                                    name.trim(),
                                    emoji,
                                    selectedCategory.label,
                                    selectedDays.sorted()
                                )
                            }
                        },
                        enabled = name.isNotBlank() && selectedDays.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MindSetColors.accentGreen,
                            contentColor = MindSetColors.background
                        )
                    ) {
                        Text("Add Habit")
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FAB Menu (choose Task or Habit)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AddItemFabMenu(
    onAddTask: () -> Unit,
    onAddHabit: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MindSetColors.surface2,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "What would you like to add?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MindSetColors.text
                )
                Spacer(Modifier.height(16.dp))

                Surface(
                    onClick = { onAddTask(); onDismiss() },
                    shape = RoundedCornerShape(12.dp),
                    color = MindSetColors.surface3,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📝", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("New Task", fontWeight = FontWeight.Medium,
                                color = MindSetColors.text, fontSize = 15.sp)
                            Text("One-time to-do item", fontSize = 12.sp,
                                color = MindSetColors.textMuted)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    onClick = { onAddHabit(); onDismiss() },
                    shape = RoundedCornerShape(12.dp),
                    color = MindSetColors.surface3,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎯", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("New Habit", fontWeight = FontWeight.Medium,
                                color = MindSetColors.text, fontSize = 15.sp)
                            Text("Recurring daily/weekly habit", fontSize = 12.sp,
                                color = MindSetColors.textMuted)
                        }
                    }
                }
            }
        }
    }
}
