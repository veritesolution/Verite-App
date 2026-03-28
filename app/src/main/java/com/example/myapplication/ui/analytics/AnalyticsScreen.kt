package com.example.myapplication.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.*
import com.example.myapplication.ui.dashboard.DashboardViewModel
import com.example.myapplication.ui.theme.MindSetColors
import com.example.myapplication.ui.dashboard.SectionHeader
import com.example.myapplication.ui.dashboard.DayOfWeekChart
import com.example.myapplication.ui.dashboard.CategoryRow
import com.example.myapplication.ui.dashboard.ClusterChip
import com.example.myapplication.ui.components.BrandedHeader

@Composable
fun AnalyticsScreen(
    viewModel: DashboardViewModel,
    onBackClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    notificationCount: Int = 0,
    onNotificationClick: () -> Unit = {}
) {
    val streaks by viewModel.streakInfos.collectAsState()
    val clusters by viewModel.clusters.collectAsState()
    val dayProfile by viewModel.dayProfile.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val momentum by viewModel.momentum.collectAsState()
    val monthlySummary by viewModel.monthlySummary.collectAsState()
    val habit30dSeries by viewModel.habit30dSeries.collectAsState()
    val taskAges by viewModel.taskAges.collectAsState()
    val weeklyMomentum by viewModel.weeklyMomentum.collectAsState()
    val taskSentiments by viewModel.taskSentiments.collectAsState()

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
                .background(MindSetColors.background)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { BrandedHeader() }

        // ── Productive Momentum ─────────────────────────────────────────────
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = MindSetColors.surface2) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Productive Momentum", fontSize = 14.sp, color = MindSetColors.textSecondary)
                    Text("${momentum.first.toInt()}%", fontSize = 48.sp, fontWeight = FontWeight.Black, color = MindSetColors.accentCyan)
                    Text(momentum.second, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MindSetColors.accentPurple)
                }
            }
        }

        // ── Weekly Momentum History (12 weeks sparkline) ────────────────────
        if (weeklyMomentum.isNotEmpty()) {
            item {
                SectionHeader("Weekly Momentum Trend")
                Surface(shape = RoundedCornerShape(16.dp), color = MindSetColors.surface2) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weeklyMomentum.forEach { week ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val height = (week.momentum * 0.7f).dp.coerceAtLeast(2.dp)
                                    val barColor = when {
                                        week.momentum >= 70f -> MindSetColors.accentGreen
                                        week.momentum >= 40f -> MindSetColors.accentOrange
                                        else -> MindSetColors.accentRed
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(height)
                                            .background(barColor, RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(weeklyMomentum.firstOrNull()?.weekLabel ?: "", fontSize = 9.sp, color = MindSetColors.textMuted)
                            Text(weeklyMomentum.lastOrNull()?.weekLabel ?: "", fontSize = 9.sp, color = MindSetColors.textMuted)
                        }
                    }
                }
            }
        }

        // ── Monthly Summary (6-month trend) ─────────────────────────────────
        if (monthlySummary.isNotEmpty()) {
            item {
                SectionHeader("Monthly Habit Completion")
                Surface(shape = RoundedCornerShape(16.dp), color = MindSetColors.surface2) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(100.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        monthlySummary.forEach { month ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val barHeight = (month.rate * 0.8f).dp.coerceAtLeast(2.dp)
                                Text("${month.rate.toInt()}%", fontSize = 9.sp, color = MindSetColors.textMuted)
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(barHeight)
                                        .background(MindSetColors.accentCyan, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(month.month.take(3), fontSize = 10.sp, color = MindSetColors.textMuted)
                            }
                        }
                    }
                }
            }
        }

        // ── 30-Day Habit Trend ──────────────────────────────────────────────
        if (habit30dSeries.isNotEmpty()) {
            item {
                SectionHeader("30-Day Habit Trend")
                Surface(shape = RoundedCornerShape(16.dp), color = MindSetColors.surface2) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            habit30dSeries.forEach { (_, pct) ->
                                val h = (pct * 0.5f).dp.coerceAtLeast(1.dp)
                                val color = when {
                                    pct >= 80f -> MindSetColors.accentGreen
                                    pct >= 50f -> MindSetColors.accentCyan
                                    else -> MindSetColors.accentRed
                                }
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(h)
                                        .background(color, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("30 days ago", fontSize = 9.sp, color = MindSetColors.textMuted)
                            Text("Today", fontSize = 9.sp, color = MindSetColors.textMuted)
                        }
                    }
                }
            }
        }

        // ── Day-of-Week Profile ─────────────────────────────────────────────
        if (dayProfile.isNotEmpty()) {
            item {
                SectionHeader("Weekly Performance Profile")
                DayOfWeekChart(dayProfile)
            }
        }

        // ── Behavioral Segmentation ─────────────────────────────────────────
        if (clusters.isNotEmpty()) {
            item {
                SectionHeader("Behavioral Segmentation")
                Text("AI has clustered your habits based on consistency patterns:", fontSize = 11.sp, color = MindSetColors.textMuted)
                Spacer(Modifier.height(8.dp))
                clusters.forEach { cluster ->
                    ClusterChip(cluster)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // ── Category Focus ──────────────────────────────────────────────────
        if (categoryBreakdown.isNotEmpty()) {
            item {
                SectionHeader("Category Focus")
                categoryBreakdown.forEach { cat ->
                    CategoryRow(cat)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // ── Task Age Warning (stale tasks) ──────────────────────────────────
        val staleTasks = taskAges.filter { it.ageDays > 7 }
        if (staleTasks.isNotEmpty()) {
            item {
                SectionHeader("Stale Tasks")
                Text("These pending tasks are over 7 days old:", fontSize = 11.sp, color = MindSetColors.textMuted)
                Spacer(Modifier.height(8.dp))
                staleTasks.take(5).forEach { task ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MindSetColors.accentRed.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.taskName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MindSetColors.text)
                                Text("${task.priority} · ${task.category}", fontSize = 10.sp, color = MindSetColors.textMuted)
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = MindSetColors.accentRed.copy(alpha = 0.2f)) {
                                Text("${task.ageDays}d", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = MindSetColors.accentRed, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Task Sentiment Breakdown ────────────────────────────────────────
        if (taskSentiments.isNotEmpty()) {
            item {
                SectionHeader("Task Sentiment Analysis")
                val positive = taskSentiments.count { it.valence == "Positive" }
                val neutral = taskSentiments.count { it.valence == "Neutral" }
                val negative = taskSentiments.count { it.valence == "Negative" }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SentimentChip("Positive", positive, MindSetColors.accentGreen)
                    SentimentChip("Neutral", neutral, MindSetColors.textSecondary)
                    SentimentChip("Negative", negative, MindSetColors.accentRed)
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
    }
}

@Composable
fun SentimentChip(label: String, count: Int, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("$count", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 11.sp, color = color)
        }
    }
}
