package com.example.myapplication.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.Task
import com.example.myapplication.ui.theme.TodoColors
import com.example.myapplication.viewmodel.TodoViewModel
import com.example.myapplication.viewmodel.TaskFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoMainScreen(
    viewModel: TodoViewModel,
    onBack: () -> Unit
) {
    val tasks by viewModel.filteredTasks.collectAsState()
    val currentFilter by viewModel.filter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<Task?>(null) }

    if (selectedTask != null) {
        TodoDetailScreen(
            task = selectedTask!!,
            onUpdate = { updatedTask ->
                viewModel.updateTask(updatedTask)
                selectedTask = null
            },
            onDelete = {
                selectedTask?.let { task -> viewModel.deleteTask(task.id) }
                selectedTask = null
            },
            onBack = { selectedTask = null }
        )
    } else {
        Scaffold(
            containerColor = TodoColors.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Todo List",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TodoColors.text
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = TodoColors.textSecondary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Profile */ }) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(id = com.example.myapplication.R.drawable.ic_account),
                                contentDescription = "Profile",
                                tint = TodoColors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TodoColors.background
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = TodoColors.accentCyan,
                    contentColor = TodoColors.background
                ) {
                    Icon(Icons.Default.Add, "Add")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TaskFilter.values()) { filter ->
                        FilterChip(
                            selected = filter == currentFilter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TodoColors.accentCyan.copy(alpha = 0.2f),
                                selectedLabelColor = TodoColors.accentCyan,
                                labelColor = TodoColors.textSecondary
                            )
                        )
                    }
                }
                
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (tasks.isEmpty()) {
                        EmptyState("No tasks found. Try changing filters or create one! 🚀")
                    } else {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            SectionHeader("Tasks", tasks.size)
                            Spacer(modifier = Modifier.weight(1f))
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = TodoColors.accentCyan,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = { viewModel.autoPrioritizeTasks() }) {
                                    Icon(Icons.Default.AutoAwesome, "Auto Prioritize", tint = TodoColors.accentCyan)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(tasks, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    onToggle = { viewModel.markTaskDone(task.id, !task.isCompleted) },
                                    onDelete = { viewModel.deleteTask(task.id) },
                                    onClick = { selectedTask = task }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddTaskDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, cat, pri ->
                    viewModel.createTask(name, cat, pri)
                    showAddDialog = false
                }
            )
        }
    }
}
