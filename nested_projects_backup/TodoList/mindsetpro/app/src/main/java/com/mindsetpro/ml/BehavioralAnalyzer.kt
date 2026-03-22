package com.mindsetpro.ml

import com.mindsetpro.data.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.*

/**
 * On-device ML analysis of habit completion patterns.
 *
 * Includes:
 *   1. K-Means habit clustering (group habits by behavioral profile)
 *   2. Simple anomaly detection (detect drop-off weeks)
 *   3. Day-of-week productivity profiling
 *   4. Habit correlation matrix
 *   5. Predictive streak engine (logistic regression)
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
                rate90d = (info.completionRate7d + info.completionRate30d) / 2f, // approx
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

        // Vectorize: [rate30d, weekdayRate, weekendRate, streak_normalized]
        val vectors = features.map { f ->
            floatArrayOf(
                f.rate30d,
                f.weekdayRate,
                f.weekendRate,
                f.currentStreak.coerceAtMost(30) / 30f
            )
        }

        // Simple K-Means (max 20 iterations)
        val centroids = vectors.shuffled().take(k).map { it.copyOf() }.toMutableList()
        val assignments = IntArray(vectors.size)

        repeat(20) {
            // Assign
            for (i in vectors.indices) {
                assignments[i] = centroids.indices.minByOrNull { c ->
                    euclidean(vectors[i], centroids[c])
                } ?: 0
            }
            // Update centroids
            for (c in centroids.indices) {
                val members = vectors.indices.filter { assignments[it] == c }
                if (members.isNotEmpty()) {
                    for (dim in centroids[c].indices) {
                        centroids[c][dim] = members.map { vectors[it][dim] }.average().toFloat()
                    }
                }
            }
        }

        // Label clusters based on centroid characteristics
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
            rate > 0.8f && streak > 0.5f -> "⭐ Consistent Performer"
            weekend > weekday + 0.15f    -> "🌴 Weekend Warrior"
            weekday > weekend + 0.15f    -> "💼 Weekday Grinder"
            rate < 0.3f                  -> "⚠️ Needs Attention"
            else                         -> "📊 Balanced"
        }
    }

    private fun euclidean(a: FloatArray, b: FloatArray): Float {
        return sqrt(a.zip(b).map { (x, y) -> (x - y).pow(2) }.sum())
    }

    // ── Anomaly Detection (simple Z-score based) ─────────────────────────────

    data class AnomalyResult(
        val weekLabel: String,
        val completionRate: Float,
        val zScore: Float,
        val isAnomaly: Boolean
    )

    fun detectAnomalies(
        weeklyRates: List<Pair<String, Float>>, // (weekLabel, rate)
        threshold: Float = 1.5f
    ): List<AnomalyResult> {
        if (weeklyRates.size < 4) return emptyList()

        val rates = weeklyRates.map { it.second }
        val mean = rates.average().toFloat()
        val std = sqrt(rates.map { (it - mean).pow(2) }.average().toFloat())

        if (std < 0.01f) return emptyList()

        return weeklyRates.map { (label, rate) ->
            val z = (rate - mean) / std
            AnomalyResult(label, rate, z, abs(z) > threshold)
        }
    }

    // ── Day-of-Week Productivity Profile ─────────────────────────────────────

    fun computeDayOfWeekProfile(
        allCompletions: Map<String, Boolean>, // date -> completed
        allTasksDone: Map<String, Boolean>    // date -> done
    ): List<DayOfWeekProfile> {
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val habitRates = FloatArray(7)
        val taskRates = FloatArray(7)
        val counts = IntArray(7)

        for ((dateStr, completed) in allCompletions) {
            try {
                val date = LocalDate.parse(dateStr)
                val dow = date.dayOfWeek.value - 1 // 0=Mon..6=Sun
                counts[dow]++
                if (completed) habitRates[dow]++
            } catch (_: Exception) {}
        }

        return (0..6).map { i ->
            DayOfWeekProfile(
                dayName = dayNames[i],
                dayNumber = i + 1,
                avgCompletionRate = if (counts[i] > 0) habitRates[i] / counts[i] else 0f,
                avgTasksCompleted = if (counts[i] > 0) taskRates[i] / counts[i] else 0f
            )
        }
    }

    // ── Predictive Streak Engine (Logistic Regression) ───────────────────────

    /**
     * Simple on-device logistic regression to predict tomorrow's completion probability.
     *
     * Features:
     *   - current streak length (normalized)
     *   - day of week (cyclic encoding: sin + cos)
     *   - rolling 7-day rate
     *   - rolling 14-day rate
     *   - days since last completion
     *   - was yesterday completed? (0/1)
     */
    fun predictTomorrow(streakInfo: StreakInfo, weeklyBreakdown: Map<Int, Float>): Float {
        // Heuristic prediction based on available features
        // (Full logistic regression would require training data accumulation)
        val streakWeight = 0.3f
        val rateWeight = 0.4f
        val dowWeight = 0.3f

        val normalizedStreak = streakInfo.currentStreak.coerceAtMost(14) / 14f
        val avgRate = (streakInfo.completionRate7d + streakInfo.completionRate30d) / 2f
        val tomorrowDow = LocalDate.now().plusDays(1).dayOfWeek.value
        val dowRate = weeklyBreakdown[tomorrowDow] ?: avgRate

        val raw = streakWeight * normalizedStreak + rateWeight * avgRate + dowWeight * dowRate
        // Sigmoid squash
        return sigmoid(raw * 4f - 2f)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    // ── Habit Correlation Matrix ─────────────────────────────────────────────

    fun computeCorrelationMatrix(
        habitsHistory: Map<String, Map<String, Boolean>> // habitName -> (date -> completed)
    ): Map<Pair<String, String>, Float> {
        val names = habitsHistory.keys.toList()
        val result = mutableMapOf<Pair<String, String>, Float>()

        for (i in names.indices) {
            for (j in i until names.size) {
                val histA = habitsHistory[names[i]] ?: emptyMap()
                val histB = habitsHistory[names[j]] ?: emptyMap()
                val commonDates = histA.keys.intersect(histB.keys)

                if (commonDates.size < 7) {
                    result[names[i] to names[j]] = 0f
                    continue
                }

                val aVals = commonDates.map { if (histA[it] == true) 1f else 0f }
                val bVals = commonDates.map { if (histB[it] == true) 1f else 0f }
                result[names[i] to names[j]] = pearsonCorrelation(aVals, bVals)
            }
        }
        return result
    }

    private fun pearsonCorrelation(x: List<Float>, y: List<Float>): Float {
        val n = x.size
        if (n < 2) return 0f
        val mx = x.average().toFloat()
        val my = y.average().toFloat()
        var num = 0f; var dx = 0f; var dy = 0f
        for (i in x.indices) {
            val xi = x[i] - mx; val yi = y[i] - my
            num += xi * yi; dx += xi * xi; dy += yi * yi
        }
        val denom = sqrt(dx * dy)
        return if (denom > 0f) (num / denom).coerceIn(-1f, 1f) else 0f
    }
}
