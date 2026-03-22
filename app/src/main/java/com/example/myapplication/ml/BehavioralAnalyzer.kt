package com.example.myapplication.ml

import com.example.myapplication.data.model.*
import java.time.LocalDate
import kotlin.math.*

/**
 * On-device ML analysis of habit completion patterns.
 */
object BehavioralAnalyzer {

    // ── Feature Engineering ──────────────────────────────────────────────────

    data class HabitFeatures(
        val habitId: String,
        val habitName: String,
        val rate7d: Float,
        val rate30d: Float,
        val rate90d: Float,
        val currentStreak: Int,
        val longestStreak: Int,
        val weekdayRate: Float,
        val weekendRate: Float,
        val totalCompletions: Int
    )

    fun buildFeatures(
        streakInfos: List<StreakInfo>,
        weeklyBreakdowns: Map<String, Map<Int, Float>>
    ): List<HabitFeatures> {
        return streakInfos.map { info ->
            val weekly = weeklyBreakdowns[info.habitId] ?: emptyMap()
            val weekdayRate = (1..5).mapNotNull { weekly[it] }.average().toFloat()
            val weekendRate = (6..7).mapNotNull { weekly[it] }.average().toFloat()

            HabitFeatures(
                habitId = info.habitId,
                habitName = info.habitName,
                rate7d = info.completionRate7d,
                rate30d = info.completionRate30d,
                rate90d = (info.completionRate7d + info.completionRate30d) / 2f,
                currentStreak = info.currentStreak,
                longestStreak = info.longestStreak,
                weekdayRate = weekdayRate,
                weekendRate = weekendRate,
                totalCompletions = (info.completionRate30d * 30).toInt()
            )
        }
    }

    // ── K-Means Clustering ───────────────────────────────────────────────────

    fun clusterHabits(features: List<HabitFeatures>, k: Int = 3): List<HabitCluster> {
        if (features.size < k) return features.map {
            HabitCluster(it.habitName, 0, "Insufficient Data", emptyMap())
        }

        val vectors = features.map { f ->
            floatArrayOf(
                f.rate30d,
                f.weekdayRate,
                f.weekendRate,
                f.currentStreak.coerceAtMost(30) / 30f
            )
        }

        val centroids = vectors.shuffled().take(k).map { it.copyOf() }.toMutableList()
        val assignments = IntArray(vectors.size)

        repeat(20) {
            for (i in vectors.indices) {
                assignments[i] = centroids.indices.minByOrNull { c ->
                    euclidean(vectors[i], centroids[c])
                } ?: 0
            }
            for (c in centroids.indices) {
                val members = vectors.indices.filter { assignments[it] == c }
                if (members.isNotEmpty()) {
                    for (dim in centroids[c].indices) {
                        centroids[c][dim] = members.map { vectors[it][dim] }.average().toFloat()
                    }
                }
            }
        }

        val labels = centroids.mapIndexed { idx, centroid ->
            idx to labelCluster(centroid)
        }.toMap()

        return features.mapIndexed { i, f ->
            HabitCluster(
                habitName = f.habitName,
                clusterId = assignments[i],
                clusterLabel = labels[assignments[i]] ?: "Unknown",
                features = mapOf(
                    "rate30d" to f.rate30d,
                    "weekdayRate" to f.weekdayRate,
                    "weekendRate" to f.weekendRate,
                    "streak" to f.currentStreak.toFloat()
                )
            )
        }
    }

    private fun labelCluster(centroid: FloatArray): String {
        val (rate, weekday, weekend, streak) = centroid.toList()
        return when {
            rate > 0.8f && streak > 0.5f -> "Consistent Performer"
            weekend > weekday + 0.15f    -> "Weekend Warrior"
            weekday > weekend + 0.15f    -> "Weekday Grinder"
            rate < 0.3f                  -> "Needs Attention"
            else                         -> "Balanced"
        }
    }

    private fun euclidean(a: FloatArray, b: FloatArray): Float {
        return sqrt(a.zip(b).map { (x, y) -> (x - y).pow(2) }.sum())
    }

    // ── Day-of-Week Profile ──────────────────────────────────────────────────

    fun computeDayOfWeekProfile(
        allCompletions: Map<String, Boolean>,
        allTasksDone: Map<String, Boolean>
    ): List<DayOfWeekProfile> {
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val habitRates = FloatArray(7)
        val counts = IntArray(7)

        for ((dateStr, completed) in allCompletions) {
            try {
                val date = LocalDate.parse(dateStr)
                val dow = date.dayOfWeek.value - 1
                counts[dow]++
                if (completed) habitRates[dow]++
            } catch (_: Exception) {}
        }

        return (0..6).map { i ->
            DayOfWeekProfile(
                dayName = dayNames[i],
                dayNumber = i + 1,
                avgCompletionRate = if (counts[i] > 0) habitRates[i] / counts[i] else 0f,
                avgTasksCompleted = 0f // Placeholder
            )
        }
    }

    // ── Predictive Streak Engine ─────────────────────────────────────────────

    fun predictTomorrow(streakInfo: StreakInfo, weeklyBreakdown: Map<Int, Float>): Float {
        val streakWeight = 0.3f
        val rateWeight = 0.4f
        val dowWeight = 0.3f

        val normalizedStreak = streakInfo.currentStreak.coerceAtMost(14) / 14f
        val avgRate = (streakInfo.completionRate7d + streakInfo.completionRate30d) / 2f
        val tomorrowDow = LocalDate.now().plusDays(1).dayOfWeek.value
        val dowRate = weeklyBreakdown[tomorrowDow] ?: avgRate

        val raw = streakWeight * normalizedStreak + rateWeight * avgRate + dowWeight * dowRate
        return 1f / (1f + exp(-(raw * 4f - 2f)))
    }

    /**
     * Returns risk label based on prediction probability (from Phase 7).
     */
    fun riskLabel(probability: Float): String = when {
        probability < 0.4f  -> "Low"
        probability < 0.70f -> "Medium"
        else                -> "High"
    }

    // ── Anomaly / Dropoff Week Detection (Phase 6) ───────────────────────────

    data class DropoffWeek(
        val weekLabel: String,
        val completionRate: Float
    )

    /**
     * Flags weeks where habit completion dropped below 50%.
     */
    fun detectDropoffWeeks(
        weeklyCompletionRates: List<Pair<String, Float>>
    ): List<DropoffWeek> {
        return weeklyCompletionRates
            .filter { it.second < 50f }
            .map { DropoffWeek(it.first, it.second) }
    }

    // ── Habit Correlation Matrix (Phase 6) ───────────────────────────────────

    data class HabitCorrelation(
        val habitA: String,
        val habitB: String,
        val correlation: Float
    )

    /**
     * Computes Pearson correlation between pairs of habits based on daily completions.
     * Returns pairs with |correlation| > 0.3 for meaningful relationships.
     */
    fun habitCorrelationMatrix(
        habitCompletions: Map<String, Map<String, Boolean>> // habitName -> (dateStr -> completed)
    ): List<HabitCorrelation> {
        val names = habitCompletions.keys.toList()
        if (names.size < 2) return emptyList()

        // Collect all dates
        val allDates = habitCompletions.values.flatMap { it.keys }.distinct().sorted()
        if (allDates.size < 7) return emptyList()

        // Build binary vectors
        val vectors = names.map { name ->
            allDates.map { date ->
                if (habitCompletions[name]?.get(date) == true) 1f else 0f
            }
        }

        val results = mutableListOf<HabitCorrelation>()
        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                val corr = pearsonCorrelation(vectors[i], vectors[j])
                if (abs(corr) > 0.3f) {
                    results.add(HabitCorrelation(names[i], names[j], corr))
                }
            }
        }
        return results.sortedByDescending { abs(it.correlation) }
    }

    private fun pearsonCorrelation(x: List<Float>, y: List<Float>): Float {
        val n = x.size
        if (n == 0) return 0f
        val meanX = x.average().toFloat()
        val meanY = y.average().toFloat()
        var num = 0f
        var denomX = 0f
        var denomY = 0f
        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            num += dx * dy
            denomX += dx * dx
            denomY += dy * dy
        }
        val denom = sqrt(denomX * denomY)
        return if (denom > 0f) num / denom else 0f
    }
}
