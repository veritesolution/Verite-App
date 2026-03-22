package com.example.myapplication.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.Task
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.MindSetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: Task,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    SkyBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Task Details", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Main Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0D2B28), // Emerald dark mode blend
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MindSetColors.accentCyan.copy(alpha = 0.2f),
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Text(
                                    text = task.category.take(1).uppercase(),
                                    color = MindSetColors.accentCyan,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = task.task,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Status: ${if(task.done) "Completed" else "In Progress"}",
                                    fontSize = 14.sp,
                                    color = MindSetColors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Divider(color = MindSetColors.surface3)
                        Spacer(modifier = Modifier.height(24.dp))

                        // Info Row
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, tint = MindSetColors.textSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Time", color = MindSetColors.textSecondary, fontSize = 14.sp)
                                }
                                Text("10:00 AM - 05:30 PM", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Priority", color = MindSetColors.textSecondary, fontSize = 14.sp)
                                val priorityColor = when(task.priority) {
                                    "High" -> Color(0xFFE57373)
                                    "Medium" -> MindSetColors.accentCyan
                                    else -> MindSetColors.accentGreen
                                }
                                Text(task.priority, color = priorityColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Delete Button
                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE57373).copy(alpha = 0.2f),
                        contentColor = Color(0xFFE57373)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Task")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Task", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
