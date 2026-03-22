package com.mindsetpro.ui.habits

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindsetpro.data.model.*
import com.mindsetpro.ui.theme.MindSetColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Habit Detail Screen with:
 *   • Current/longest streak display
 *   • 7-day and 30-day completion rates
 *   • Weekly breakdown bar chart
 *   • 90-day completion heatmap calendar
 *   • Tomorrow's prediction indicator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    habit: Habit,
    streakInfo: StreakInfo,
    weeklyBreakdown: Map<Int, Float>,
    heatmapData: Map<String, Boolean>,
    prediction: Float?,
    onToggleToday: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val today = LocalDate.now()
    val isTodayDone = heatmapData[today.toString()] == true
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, "Delete", tint = MindSetColors.accentRed)
            }
        }

        // ── Header ───────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(habit.emoji, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(habit.name, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = MindSetColors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MindSetColors.categoryColor(habit.category).copy(alpha = 0.15f)
                    ) {
                        Text(habit.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            color = MindSetColors.categoryColor(habit.category))
                    }
                    val targetDays = habit.targetDays.split(",").map { it.trim().toInt() }
                    val dayLabel = when {
                        targetDays.size == 7 -> "Every day"
                        targetDays == listOf(1,2,3,4,5) -> "Weekdays"
                        targetDays == listOf(6,7) -> "Weekends"
                        else -> "${targetDays.size} days/week"
                    }
                    Text(dayLabel, fontSize = 11.sp, color = MindSetColors.textMuted)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Today Toggle ─────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isTodayDone) MindSetColors.accentGreen.copy(alpha = 0.12f)
                    else MindSetColors.surface2,
            onClick = onToggleToday,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    if (isTodayDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    null,
                    tint = if (isTodayDone) MindSetColors.accentGreen else MindSetColors.textMuted,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isTodayDone) "Done for today! ✨" else "Tap to complete today",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isTodayDone) MindSetColors.accentGreen else MindSetColors.text
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Streak Stats Row ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("🔥 Current", "${streakInfo.currentStreak}d",
                MindSetColors.accentOrange, Modifier.weight(1f))
            StatCard("🏆 Best", "${streakInfo.longestStreak}d",
                MindSetColors.accentYellow, Modifier.weight(1f))
            StatCard("📅 7d Rate", "${(streakInfo.completionRate7d * 100).toInt()}%",
                MindSetColors.accentCyan, Modifier.weight(1f))
            StatCard("📊 30d Rate", "${(streakInfo.completionRate30d * 100).toInt()}%",
                MindSetColors.accentPurple, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        // ── Tomorrow's Prediction ────────────────────────────────────────────
        if (prediction != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MindSetColors.surface2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔮", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tomorrow's Prediction", fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, color = MindSetColors.text)
                        Text("Based on your patterns & streaks", fontSize = 11.sp,
                            color = MindSetColors.textMuted)
                    }
                    val pctText = "${(prediction * 100).toInt()}%"
                    val color = when {
                        prediction > 0.7f -> MindSetColors.accentGreen
                        prediction > 0.4f -> MindSetColors.accentYellow
                        else -> MindSetColors.accentRed
                    }
                    Text(pctText, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Weekly Breakdown ─────────────────────────────────────────────────
        Text("📊 Day-of-Week Pattern", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = MindSetColors.text)
        Spacer(Modifier.height(12.dp))

        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            (1..7).forEach { dow ->
                val rate = weeklyBreakdown[dow] ?: 0f
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(rate * 100).toInt()}%", fontSize = 9.sp, color = MindSetColors.textMuted)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height((rate * 60).dp.coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(MindSetColors.accentCyan, MindSetColors.accentPurple)
                                )
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(dayNames[dow - 1], fontSize = 10.sp, color = MindSetColors.textMuted)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── 90-Day Heatmap Calendar ──────────────────────────────────────────
        Text("🗓️ Last 90 Days", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = MindSetColors.text)
        Spacer(Modifier.height(12.dp))

        HeatmapCalendar(heatmapData)
    }

    // ── Delete Confirmation ──────────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Habit?", color = MindSetColors.text) },
            text = { Text("\"${habit.name}\" and all history will be permanently removed.",
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

// ── Stat Card ────────────────────────────────────────────────────────────────

@Composable
fun StatCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MindSetColors.surface2,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 9.sp, color = MindSetColors.textMuted, maxLines = 1)
        }
    }
}

// ── 90-Day Heatmap Calendar ──────────────────────────────────────────────────

@Composable
fun HeatmapCalendar(data: Map<String, Boolean>) {
    val today = LocalDate.now()
    val startDate = today.minusDays(89)

    // Build grid: 13 columns (weeks) × 7 rows (days)
    val grid = Array(7) { arrayOfNulls<Pair<LocalDate, Boolean>>(13) }
    var d = startDate
    while (!d.isAfter(today)) {
        val weekIndex = ((d.toEpochDay() - startDate.toEpochDay()) / 7).toInt()
        val dayIndex = d.dayOfWeek.value - 1 // 0=Mon..6=Sun
        if (weekIndex in 0..12) {
            grid[dayIndex][weekIndex] = d to (data[d.toString()] == true)
        }
        d = d.plusDays(1)
    }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
        grid.forEachIndexed { rowIdx, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    dayLabels[rowIdx],
                    fontSize = 9.sp,
                    color = MindSetColors.textMuted,
                    modifier = Modifier.width(14.dp)
                )
                row.forEach { cell ->
                    val color = when {
                        cell == null -> MindSetColors.surface2.copy(alpha = 0.3f)
                        cell.second -> MindSetColors.accentGreen
                        cell.first == today -> MindSetColors.accentCyan.copy(alpha = 0.3f)
                        else -> MindSetColors.surface3
                    }
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }
        }

        // Legend
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 14.dp)
        ) {
            LegendDot(MindSetColors.surface3, "Missed")
            LegendDot(MindSetColors.accentGreen, "Done")
            LegendDot(MindSetColors.accentCyan.copy(alpha = 0.3f), "Today")
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = MindSetColors.textMuted)
    }
}
