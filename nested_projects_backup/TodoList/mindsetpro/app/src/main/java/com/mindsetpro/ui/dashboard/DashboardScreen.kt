package com.mindsetpro.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindsetpro.data.model.*
import com.mindsetpro.ui.theme.MindSetColors

/**
 * Main Dashboard Screen — mirrors the 12-panel analytics dashboard from the notebook.
 *
 * Row 1: KPI Tiles (Habits %, Tasks %, Max Streak, Momentum)
 * Row 2: Today's Habits checklist + Today's Tasks
 * Row 3: Streak leaderboard + Predictions
 * Row 4: Category breakdown + DOW profile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    val momentum by viewModel.momentum.collectAsState()
    val streaks by viewModel.streakInfos.collectAsState()
    val habits by viewModel.allHabits.collectAsState()
    val tasks by viewModel.pendingTasks.collectAsState()
    val clusters by viewModel.clusters.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val dayProfile by viewModel.dayProfile.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MindSetColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        item {
            Text(
                "🧠 MindSet Pro",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MindSetColors.text
            )
            Text(
                "AI-Powered Habit & Task Intelligence",
                fontSize = 12.sp,
                color = MindSetColors.textMuted
            )
        }

        // ── KPI Row ──────────────────────────────────────────────────────────
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

        // ── Pending High-Priority Tasks ──────────────────────────────────────
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
                            "⚠️ ${snapshot.pendingHigh} high-priority task(s) pending",
                            color = MindSetColors.accentRed,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // ── Today's Tasks ────────────────────────────────────────────────────
        item {
            SectionHeader("📋 Today's Tasks", tasks.size)
        }
        if (tasks.isEmpty()) {
            item {
                EmptyState("No pending tasks. Nice work! 🎉")
            }
        } else {
            items(tasks.take(5), key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onToggle = { viewModel.markTaskDone(task.id, !task.done) },
                    onDelete = { viewModel.deleteTask(task.id) }
                )
            }
        }

        // ── Today's Habits ───────────────────────────────────────────────────
        item {
            SectionHeader("🎯 Today's Habits", habits.size)
        }
        items(habits, key = { it.id }) { habit ->
            HabitCard(
                habit = habit,
                streakInfo = streaks.find { it.habitId == habit.id },
                prediction = predictions[habit.name],
                onToggle = { viewModel.toggleHabit(habit.id) }
            )
        }

        // ── Streak Leaderboard ───────────────────────────────────────────────
        if (streaks.isNotEmpty()) {
            item { SectionHeader("🔥 Streak Leaderboard") }
            items(streaks.sortedByDescending { it.currentStreak }.take(5)) { info ->
                StreakRow(info)
            }
        }

        // ── Predictions ──────────────────────────────────────────────────────
        if (predictions.isNotEmpty()) {
            item { SectionHeader("🔮 Tomorrow's Predictions") }
            items(predictions.entries.toList().sortedByDescending { it.value }) { (name, prob) ->
                PredictionRow(name, prob)
            }
        }

        // ── Habit Clusters ───────────────────────────────────────────────────
        if (clusters.isNotEmpty()) {
            item { SectionHeader("🤖 Behavioral Clusters") }
            items(clusters) { cluster ->
                ClusterChip(cluster)
            }
        }

        // ── Day-of-Week Profile ──────────────────────────────────────────────
        if (dayProfile.isNotEmpty()) {
            item { SectionHeader("📊 Day-of-Week Profile") }
            item { DayOfWeekChart(dayProfile) }
        }

        // ── Category Breakdown ───────────────────────────────────────────────
        if (categoryBreakdown.isNotEmpty()) {
            item { SectionHeader("📂 Category Breakdown") }
            items(categoryBreakdown) { cat ->
                CategoryRow(cat)
            }
        }

        // Footer spacer
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Component: KPI Tile ──────────────────────────────────────────────────────

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

// ── Component: Task Card ─────────────────────────────────────────────────────

@Composable
fun TaskCard(task: Task, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MindSetColors.surface2
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MindSetColors.accentGreen,
                    uncheckedColor = MindSetColors.textMuted
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.name,
                    color = if (task.done) MindSetColors.textMuted else MindSetColors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryBadge(task.category)
                    PriorityBadge(task.priority)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, null, tint = MindSetColors.textMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Component: Habit Card ────────────────────────────────────────────────────

@Composable
fun HabitCard(habit: Habit, streakInfo: StreakInfo?, prediction: Float?, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(10.dp),
        color = MindSetColors.surface2
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(habit.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, color = MindSetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (streakInfo != null) {
                        Text("🔥 ${streakInfo.currentStreak}d", fontSize = 11.sp, color = MindSetColors.accentOrange)
                        Text("${(streakInfo.completionRate7d * 100).toInt()}% 7d", fontSize = 11.sp, color = MindSetColors.textMuted)
                    }
                    if (prediction != null) {
                        Text("🔮 ${(prediction * 100).toInt()}%", fontSize = 11.sp, color = MindSetColors.accentPurple)
                    }
                }
            }
            CategoryBadge(habit.category)
        }
    }
}

// ── Component: Streak Row ────────────────────────────────────────────────────

@Composable
fun StreakRow(info: StreakInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(info.habitName, modifier = Modifier.weight(1f), color = MindSetColors.text, fontSize = 13.sp)
        Text("${info.currentStreak}d", color = MindSetColors.accentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text("best: ${info.longestStreak}d", color = MindSetColors.textMuted, fontSize = 11.sp)
    }
}

// ── Component: Prediction Row ────────────────────────────────────────────────

@Composable
fun PredictionRow(habitName: String, probability: Float) {
    val color = when {
        probability > 0.7f -> MindSetColors.accentGreen
        probability > 0.4f -> MindSetColors.accentYellow
        else -> MindSetColors.accentRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(habitName, modifier = Modifier.weight(1f), color = MindSetColors.text, fontSize = 13.sp)
        LinearProgressIndicator(
            progress = { probability },
            modifier = Modifier.width(80.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MindSetColors.surface3
        )
        Spacer(Modifier.width(8.dp))
        Text("${(probability * 100).toInt()}%", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Component: Cluster Chip ──────────────────────────────────────────────────

@Composable
fun ClusterChip(cluster: HabitCluster) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MindSetColors.surface3.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(cluster.habitName, modifier = Modifier.weight(1f), color = MindSetColors.text, fontSize = 13.sp)
            Text(cluster.clusterLabel, color = MindSetColors.accentPurple, fontSize = 11.sp)
        }
    }
}

// ── Component: Day-of-Week Chart (simple bar) ────────────────────────────────

@Composable
fun DayOfWeekChart(profile: List<DayOfWeekProfile>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        profile.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height((day.avgCompletionRate * 80).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(MindSetColors.accentCyan, MindSetColors.accentPurple)
                            )
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(day.dayName, fontSize = 10.sp, color = MindSetColors.textMuted)
                Text("${(day.avgCompletionRate * 100).toInt()}%", fontSize = 9.sp, color = MindSetColors.textSecondary)
            }
        }
    }
}

// ── Component: Category Row ──────────────────────────────────────────────────

@Composable
fun CategoryRow(cat: CategoryBreakdown) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MindSetColors.categoryColor(cat.category))
        )
        Spacer(Modifier.width(8.dp))
        Text(cat.category, modifier = Modifier.weight(1f), color = MindSetColors.text, fontSize = 13.sp)
        Text("${cat.completedItems}/${cat.totalItems}", color = MindSetColors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { cat.completionRate },
            modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = MindSetColors.categoryColor(cat.category),
            trackColor = MindSetColors.surface3
        )
    }
}

// ── Utility Components ───────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MindSetColors.text)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = MindSetColors.surface3) {
                Text("$count", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp, color = MindSetColors.textSecondary)
            }
        }
    }
}

@Composable
fun CategoryBadge(category: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MindSetColors.categoryColor(category).copy(alpha = 0.15f)
    ) {
        Text(category, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp, color = MindSetColors.categoryColor(category))
    }
}

@Composable
fun PriorityBadge(priority: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MindSetColors.priorityColor(priority).copy(alpha = 0.15f)
    ) {
        Text(priority, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp, color = MindSetColors.priorityColor(priority))
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MindSetColors.textMuted, fontSize = 13.sp)
    }
}
