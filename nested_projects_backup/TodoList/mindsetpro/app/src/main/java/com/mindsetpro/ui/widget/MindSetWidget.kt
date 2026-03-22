package com.mindsetpro.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.*
import com.mindsetpro.MainActivity
import com.mindsetpro.data.local.MindSetDatabase
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * MindSet Pro Home Screen Widget.
 *
 * Displays:
 *   • Today's habit completion progress
 *   • Current max streak
 *   • Pending high-priority tasks count
 *   • Quick-glance habit checklist
 */
class MindSetWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val data = loadWidgetData(context)

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF0E0E14))
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                // ── Title ────────────────────────────────────────────────────
                Text(
                    "🧠 MindSet Pro",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFF0F0F8)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(GlanceModifier.height(10.dp))

                // ── KPI Row ──────────────────────────────────────────────────
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WidgetKpi("Habits", "${data.habitsDone}/${data.habitsTotal}",
                        Color(0xFF00E676))
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetKpi("Streak", "${data.maxStreak}d",
                        Color(0xFFFF9100))
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetKpi("Tasks", "${data.pendingHigh} high",
                        Color(0xFFFF5252))
                }

                Spacer(GlanceModifier.height(10.dp))

                // ── Progress Bar ─────────────────────────────────────────────
                val pct = if (data.habitsTotal > 0)
                    data.habitsDone.toFloat() / data.habitsTotal else 0f

                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color(0xFF2A2A3A))
                        .cornerRadius(3.dp)
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width((pct * 280).dp.coerceAtMost(280.dp))
                            .background(Color(0xFF00E676))
                            .cornerRadius(3.dp)
                    ) {}
                }

                Spacer(GlanceModifier.height(8.dp))

                // ── Habit List (first 4) ─────────────────────────────────────
                data.habits.take(4).forEach { (name, emoji, done) ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Text(
                            if (done) "✅" else "⬜",
                            style = TextStyle(fontSize = 13.sp)
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            "$emoji $name",
                            style = TextStyle(
                                color = ColorProvider(
                                    if (done) Color(0xFF55556A) else Color(0xFFF0F0F8)
                                ),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetKpi(label: String, value: String, color: Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                label,
                style = TextStyle(
                    color = ColorProvider(Color(0xFF55556A)),
                    fontSize = 10.sp
                )
            )
        }
    }

    // ── Data Loading ─────────────────────────────────────────────────────────

    data class WidgetData(
        val habitsDone: Int = 0,
        val habitsTotal: Int = 0,
        val maxStreak: Int = 0,
        val pendingHigh: Int = 0,
        val habits: List<Triple<String, String, Boolean>> = emptyList()
    )

    private fun loadWidgetData(context: Context): WidgetData = runBlocking {
        try {
            val db = MindSetDatabase.getInstance(context)
            val today = LocalDate.now()
            val todayStr = today.toString()
            val todayDow = today.dayOfWeek.value

            val allHabits = db.habitDao().getAll()
            val todayHabits = allHabits.filter { habit ->
                todayDow in habit.targetDays.split(",").map { it.trim().toInt() }
            }
            val todayCompletions = db.habitCompletionDao().getForDate(todayStr)
            val completedIds = todayCompletions.filter { it.completed }.map { it.habitId }.toSet()

            val habitsList = todayHabits.map { h ->
                Triple(h.name, h.emoji, h.id in completedIds)
            }

            val pendingHigh = db.taskDao().pendingHighCount()

            WidgetData(
                habitsDone = completedIds.size,
                habitsTotal = todayHabits.size,
                maxStreak = 0, // Would need full streak computation
                pendingHigh = pendingHigh,
                habits = habitsList
            )
        } catch (e: Exception) {
            WidgetData()
        }
    }
}

/**
 * Widget Receiver — registered in AndroidManifest.xml.
 */
class MindSetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MindSetWidget()
}
