package com.mindsetpro.ui.analytics

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindsetpro.data.model.*
import com.mindsetpro.ui.theme.MindSetColors

/**
 * 📊 Analytics Detail Screen
 *
 * Rich visualization dashboard mirroring the notebook's 12-panel layout:
 *   • 30-day completion timeseries
 *   • Habit heatmap
 *   • Category & Priority breakdown (donut charts)
 *   • Day-of-week productivity profile
 *   • Cluster scatter view
 *   • Momentum trend
 */
@Composable
fun AnalyticsScreen(
    snapshot: DashboardSnapshot,
    streakInfos: List<StreakInfo>,
    clusters: List<HabitCluster>,
    dayProfile: List<DayOfWeekProfile>,
    categoryBreakdown: List<CategoryBreakdown>,
    momentumScore: Float,
    momentumLabel: String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MindSetColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        item {
            Text(
                "📊 Analytics Dashboard",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MindSetColors.text
            )
        }

        // ── Momentum Gauge ───────────────────────────────────────────────────
        item {
            MomentumGauge(score = momentumScore, label = momentumLabel)
        }

        // ── Habit Completion Rates (Bar Chart) ───────────────────────────────
        if (streakInfos.isNotEmpty()) {
            item {
                AnalyticsCard(title = "🎯 Habit Completion Rates (30d)") {
                    HabitBarChart(streakInfos)
                }
            }
        }

        // ── Category Donut ───────────────────────────────────────────────────
        if (categoryBreakdown.isNotEmpty()) {
            item {
                AnalyticsCard(title = "📂 Category Distribution") {
                    CategoryDonutChart(categoryBreakdown)
                }
            }
        }

        // ── Day-of-Week Heatmap ──────────────────────────────────────────────
        if (dayProfile.isNotEmpty()) {
            item {
                AnalyticsCard(title = "📅 Day-of-Week Productivity") {
                    DayOfWeekHeatmap(dayProfile)
                }
            }
        }

        // ── Cluster Summary ──────────────────────────────────────────────────
        if (clusters.isNotEmpty()) {
            item {
                AnalyticsCard(title = "🤖 Behavioral Clusters") {
                    ClusterSummary(clusters)
                }
            }
        }

        // ── Streak Rankings ──────────────────────────────────────────────────
        if (streakInfos.isNotEmpty()) {
            item {
                AnalyticsCard(title = "🏆 Streak Rankings") {
                    StreakRankingTable(streakInfos)
                }
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

// ── Momentum Gauge ───────────────────────────────────────────────────────────

@Composable
fun MomentumGauge(score: Float, label: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MindSetColors.surface2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Productive Momentum", fontSize = 13.sp, color = MindSetColors.textMuted)
            Spacer(Modifier.height(12.dp))

            // Arc gauge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                Canvas(modifier = Modifier.size(130.dp)) {
                    val sweepAngle = 240f
                    val startAngle = 150f
                    val strokeWidth = 14f

                    // Background arc
                    drawArc(
                        color = MindSetColors.surface3,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )

                    // Progress arc
                    val progressAngle = sweepAngle * (score / 100f)
                    val color = when {
                        score >= 70f -> MindSetColors.accentGreen
                        score >= 40f -> MindSetColors.accentYellow
                        else -> MindSetColors.accentRed
                    }
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = progressAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${score.toInt()}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MindSetColors.text
                    )
                    Text(label, fontSize = 11.sp, color = MindSetColors.textSecondary)
                }
            }
        }
    }
}

// ── Habit Bar Chart ──────────────────────────────────────────────────────────

@Composable
fun HabitBarChart(infos: List<StreakInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        infos.sortedByDescending { it.completionRate30d }.forEach { info ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    info.habitName.take(12),
                    fontSize = 11.sp,
                    color = MindSetColors.textSecondary,
                    modifier = Modifier.width(80.dp)
                )
                Box(modifier = Modifier.weight(1f).height(16.dp)) {
                    // Background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MindSetColors.surface3)
                    )
                    // Fill
                    val barColor = when {
                        info.completionRate30d > 0.7f -> MindSetColors.accentGreen
                        info.completionRate30d > 0.4f -> MindSetColors.accentYellow
                        else -> MindSetColors.accentRed
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(info.completionRate30d.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(info.completionRate30d * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MindSetColors.text,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

// ── Category Donut Chart ─────────────────────────────────────────────────────

@Composable
fun CategoryDonutChart(categories: List<CategoryBreakdown>) {
    val total = categories.sumOf { it.totalItems }.toFloat()
    if (total == 0f) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Donut
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            Canvas(modifier = Modifier.size(110.dp)) {
                var startAngle = -90f
                categories.forEach { cat ->
                    val sweep = (cat.totalItems / total) * 360f
                    drawArc(
                        color = MindSetColors.categoryColor(cat.category),
                        startAngle = startAngle,
                        sweepAngle = sweep - 2f,
                        useCenter = false,
                        style = Stroke(width = 24f, cap = StrokeCap.Butt),
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweep
                }
            }
            Text("${total.toInt()}", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MindSetColors.text)
        }

        // Legend
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            categories.forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MindSetColors.categoryColor(cat.category))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${cat.category} (${cat.totalItems})", fontSize = 11.sp,
                        color = MindSetColors.textSecondary)
                }
            }
        }
    }
}

// ── Day-of-Week Heatmap ──────────────────────────────────────────────────────

@Composable
fun DayOfWeekHeatmap(profile: List<DayOfWeekProfile>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        profile.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val intensity = day.avgCompletionRate.coerceIn(0f, 1f)
                val color = MindSetColors.accentGreen.copy(alpha = 0.2f + intensity * 0.8f)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${(intensity * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MindSetColors.text
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(day.dayName, fontSize = 10.sp, color = MindSetColors.textMuted)
            }
        }
    }
}

// ── Cluster Summary ──────────────────────────────────────────────────────────

@Composable
fun ClusterSummary(clusters: List<HabitCluster>) {
    val grouped = clusters.groupBy { it.clusterLabel }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        grouped.forEach { (label, habits) ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MindSetColors.surface3.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MindSetColors.accentPurple)
                    Text(
                        habits.joinToString(", ") { it.habitName },
                        fontSize = 11.sp,
                        color = MindSetColors.textSecondary
                    )
                }
            }
        }
    }
}

// ── Streak Ranking Table ─────────────────────────────────────────────────────

@Composable
fun StreakRankingTable(infos: List<StreakInfo>) {
    val sorted = infos.sortedByDescending { it.currentStreak }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Header
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Habit", modifier = Modifier.weight(1f), fontSize = 10.sp,
                fontWeight = FontWeight.Bold, color = MindSetColors.textMuted)
            Text("Current", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = MindSetColors.textMuted, modifier = Modifier.width(50.dp))
            Text("Best", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = MindSetColors.textMuted, modifier = Modifier.width(50.dp))
            Text("7d Rate", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = MindSetColors.textMuted, modifier = Modifier.width(50.dp))
        }
        HorizontalDivider(color = MindSetColors.surface3)

        sorted.forEachIndexed { index, info ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val medal = when (index) {
                    0 -> "🥇 "
                    1 -> "🥈 "
                    2 -> "🥉 "
                    else -> "    "
                }
                Text(
                    "$medal${info.habitName}",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = MindSetColors.text
                )
                Text(
                    "${info.currentStreak}d",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MindSetColors.accentOrange,
                    modifier = Modifier.width(50.dp)
                )
                Text(
                    "${info.longestStreak}d",
                    fontSize = 12.sp,
                    color = MindSetColors.textSecondary,
                    modifier = Modifier.width(50.dp)
                )
                Text(
                    "${(info.completionRate7d * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MindSetColors.accentCyan,
                    modifier = Modifier.width(50.dp)
                )
            }
        }
    }
}

// ── Utility: Analytics Card Wrapper ──────────────────────────────────────────

@Composable
fun AnalyticsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MindSetColors.surface2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MindSetColors.text)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
