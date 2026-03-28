package com.example.myapplication.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.*
import com.example.myapplication.ui.theme.MindSetColors
import com.example.myapplication.ui.components.BrandedHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTaskDetail: (String) -> Unit,
    onBackClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    notificationCount: Int = 0,
    onNotificationClick: () -> Unit = {}
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val momentum by viewModel.momentum.collectAsState()
    val streaks by viewModel.streakInfos.collectAsState()
    val habits by viewModel.allHabits.collectAsState()
    val tasks by viewModel.allTasks.collectAsState() // Using allTasks instead of pendingTasks for general view
    val clusters by viewModel.clusters.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val dayProfile by viewModel.dayProfile.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        com.example.myapplication.ui.components.VeriteTopBar(
            onBackClick = onBackClick,
            onProfileClick = onProfileClick,
            notificationCount = notificationCount,
            onNotificationClick = onNotificationClick
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item { BrandedHeader() }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiTile(
                    label = "Habits",
                    value = "${snapshot.habitsDone}/${snapshot.habitsScheduled}",
                    subtitle = "${snapshot.habitPct.toInt()}%",
                    color = MindSetColors.accentGreen,
                    modifier = Modifier.weight(1f)
                )
                KpiTile(
                    label = "Tasks",
                    value = "${snapshot.tasksDone}/${snapshot.tasksTotal}",
                    subtitle = "${snapshot.taskPct.toInt()}%",
                    color = MindSetColors.accentCyan,
                    modifier = Modifier.weight(1f)
                )
                KpiTile(
                    label = "Streak",
                    value = "${snapshot.currentMaxStreak}",
                    subtitle = "max",
                    color = MindSetColors.accentOrange,
                    modifier = Modifier.weight(1f)
                )
                KpiTile(
                    label = "Momentum",
                    value = "${momentum.first.toInt()}",
                    subtitle = momentum.second.take(2),
                    color = MindSetColors.accentPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (snapshot.pendingHigh > 0) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MindSetColors.accentRed.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MindSetColors.accentRed)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${snapshot.pendingHigh} high-priority task(s) pending",
                            color = MindSetColors.accentRed,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today's Tasks", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MindSetColors.text)
                Spacer(Modifier.width(8.dp))
                Text("(${tasks.size})", fontSize = 12.sp, color = MindSetColors.textMuted)
                Spacer(Modifier.weight(1f))
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MindSetColors.accentCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = { viewModel.autoPrioritizeTasks() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, "Auto Categorize", tint = MindSetColors.accentCyan, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        items(tasks.take(5)) { task ->
            TaskCard(
                task = task,
                onToggle = { viewModel.markTaskDone(task.id, !task.done) },
                onClick = { onNavigateToTaskDetail(task.id) }
            )
        }

        item {
            var newTaskText by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskText,
                    onValueChange = { newTaskText = it },
                    placeholder = { Text("Add new task...", color = MindSetColors.textMuted, fontSize = 14.sp) },
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
                        if (newTaskText.isNotBlank()) {
                            viewModel.createTask(newTaskText)
                            newTaskText = ""
                        }
                    },
                    modifier = Modifier.background(MindSetColors.surface2, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task", tint = MindSetColors.accentCyan)
                }
            }
        }

        item { SectionHeader("Today's Habits", habits.size) }
        items(habits) { habit ->
            HabitCard(
                habit = habit,
                streakInfo = streaks.find { it.habitId == habit.id },
                prediction = predictions[habit.name],
                onToggle = { viewModel.toggleHabit(habit.id) }
            )
        }

        if (streaks.isNotEmpty()) {
            item { SectionHeader("Streak Leaderboard") }
            items(streaks.sortedByDescending { it.currentStreak }.take(5)) { info ->
                StreakRow(info)
            }
        }

        if (predictions.isNotEmpty()) {
            item { SectionHeader("Predictions") }
            items(predictions.entries.toList()) { (name, prob) ->
                PredictionRow(name, prob)
            }
        }

        if (dayProfile.isNotEmpty()) {
            item { SectionHeader("Day-of-Week Profile") }
            item { DayOfWeekChart(dayProfile) }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
    }
}

@Composable
fun KpiTile(label: String, value: String, subtitle: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MindSetColors.surface2
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = MindSetColors.textMuted)
            Text(subtitle, fontSize = 10.sp, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun TaskCard(task: Task, onToggle: () -> Unit, onClick: () -> Unit) {
    val cardColor = when (task.priority) {
        "High" -> Color(0xFF051714) // Deep Dark Teal
        "Medium" -> Color(0xFF15403C) // Mid Dark Emerald
        else -> Color(0xFF1E1E24) // Muted Slate
    }
    val contentColor = Color.White

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 24.dp,
            bottomEnd = 8.dp
        ),
        color = cardColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.task,
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = contentColor.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "10:00 AM - 05:30 PM", // Mockup standard
                            color = contentColor.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "${task.priority} Priority",
                            color = contentColor,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (task.category.isNotBlank() && task.category != "null") {
                        Surface(
                            shape = CircleShape,
                            color = MindSetColors.accentCyan.copy(alpha=0.15f),
                            border = BorderStroke(1.dp, MindSetColors.accentCyan)
                        ) {
                            Text(
                                text = task.category,
                                color = MindSetColors.accentCyan,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            VeriteCheckbox(
                checked = task.done,
                onCheckedChange = onToggle,
                checkedColor = contentColor,
                checkmarkColor = cardColor,
                uncheckedColor = contentColor.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun HabitCard(habit: Habit, streakInfo: StreakInfo?, prediction: Float?, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onToggle() },
        shape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 8.dp,
            bottomEnd = 24.dp
        ),
        color = Color(0xFF15403C) // Match Mid Dark Emerald
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (streakInfo != null) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.3f)) {
                            Text("${streakInfo.currentStreak}d", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    if (prediction != null) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.3f)) {
                            Text("${(prediction * 100).toInt()}%", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.3f)) {
                Text(habit.category, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
fun StreakRow(info: StreakInfo) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(info.habitName, modifier = Modifier.weight(1f), color = MindSetColors.text, fontSize = 13.sp)
        Text("${info.currentStreak}d", color = MindSetColors.accentOrange, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PredictionRow(habitName: String, probability: Float) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(habitName, modifier = Modifier.weight(1f), color = MindSetColors.text, fontSize = 13.sp)
        Text("${(probability * 100).toInt()}%", color = MindSetColors.accentPurple, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DayOfWeekChart(profile: List<DayOfWeekProfile>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        profile.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.width(20.dp).height((day.avgCompletionRate * 60).dp.coerceAtLeast(4.dp))
                        .background(MindSetColors.accentCyan, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                )
                Text(day.dayName, fontSize = 10.sp, color = MindSetColors.textMuted)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MindSetColors.text)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("($count)", fontSize = 12.sp, color = MindSetColors.textMuted)
        }
    }
}

@Composable
fun CategoryBadge(category: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = MindSetColors.categoryColor(category).copy(alpha = 0.2f)) {
        Text(category, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = MindSetColors.categoryColor(category))
    }
}

@Composable
fun PriorityBadge(priority: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = MindSetColors.priorityColor(priority).copy(alpha = 0.2f)) {
        Text(priority, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = MindSetColors.priorityColor(priority))
    }
}

@Composable
fun CategoryRow(breakdown: CategoryBreakdown) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(breakdown.category)
        Spacer(Modifier.width(12.dp))
        LinearProgressIndicator(
            progress = { breakdown.completionRate },
            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
            color = MindSetColors.categoryColor(breakdown.category),
            trackColor = MindSetColors.surface3
        )
        Spacer(Modifier.width(12.dp))
        Text("${(breakdown.completionRate * 100).toInt()}%", fontSize = 12.sp, color = MindSetColors.text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ClusterChip(cluster: HabitCluster) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MindSetColors.surface2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(MindSetColors.accentCyan))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(cluster.habitName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MindSetColors.text)
                Text(cluster.clusterLabel, fontSize = 11.sp, color = MindSetColors.accentCyan)
            }
        }
    }
}

@Composable
fun VeriteCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    checkedColor: Color,
    checkmarkColor: Color,
    uncheckedColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (checked) checkedColor else Color.Transparent,
        border = BorderStroke(1.5.dp, if (checked) checkedColor else uncheckedColor),
        modifier = Modifier
            .size(24.dp)
            .clickable { onCheckedChange() }
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Done",
                tint = checkmarkColor,
                modifier = Modifier.padding(2.dp)
            )
        }
    }
}
