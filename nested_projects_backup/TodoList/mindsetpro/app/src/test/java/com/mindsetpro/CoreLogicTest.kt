package com.mindsetpro

import com.mindsetpro.data.model.Intent
import com.mindsetpro.ml.SentimentMoodTracker
import com.mindsetpro.utils.VoiceCommandProcessor
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MindSet Pro core logic.
 */
class CoreLogicTest {

    // ═════════════════════════════════════════════════════════════════════════
    // Voice Command Processor Tests
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse add task with priority`() {
        val result = VoiceCommandProcessor.parse("add task called Review PR priority high")
        assertEquals(Intent.ADD_TASK, result.intent)
        assertEquals("Review PR", result.entityName)
        assertEquals("High", result.priority)
        assertTrue(result.confidence >= 0.8f)
    }

    @Test
    fun `parse add task simple`() {
        val result = VoiceCommandProcessor.parse("create task Fix login bug")
        assertEquals(Intent.ADD_TASK, result.intent)
        assertEquals("Fix login bug", result.entityName)
    }

    @Test
    fun `parse complete task`() {
        val result = VoiceCommandProcessor.parse("mark Review PR as done")
        assertEquals(Intent.COMPLETE_TASK, result.intent)
        assertEquals("Review PR", result.entityName)
    }

    @Test
    fun `parse delete task`() {
        val result = VoiceCommandProcessor.parse("delete task old report")
        assertEquals(Intent.DELETE_TASK, result.intent)
        assertEquals("old report", result.entityName)
    }

    @Test
    fun `parse add habit`() {
        val result = VoiceCommandProcessor.parse("add habit called Morning Run category Health")
        assertEquals(Intent.ADD_HABIT, result.intent)
        assertEquals("Morning Run", result.entityName)
        assertEquals("Health", result.category)
    }

    @Test
    fun `parse toggle habit`() {
        val result = VoiceCommandProcessor.parse("I did meditation")
        assertEquals(Intent.TOGGLE_HABIT, result.intent)
        assertEquals("meditation", result.entityName)
    }

    @Test
    fun `parse query tasks`() {
        val result = VoiceCommandProcessor.parse("show my tasks")
        assertEquals(Intent.QUERY_TASKS, result.intent)
    }

    @Test
    fun `parse list habits`() {
        val result = VoiceCommandProcessor.parse("list all habits")
        assertEquals(Intent.LIST_HABITS, result.intent)
    }

    @Test
    fun `parse unknown input`() {
        val result = VoiceCommandProcessor.parse("what is the weather today")
        assertEquals(Intent.UNKNOWN, result.intent)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `parse empty input`() {
        val result = VoiceCommandProcessor.parse("")
        assertEquals(Intent.UNKNOWN, result.intent)
    }

    @Test
    fun `batch parse multiple commands`() {
        val results = VoiceCommandProcessor.parseBatch(
            "add task Buy groceries and I did meditation then show my tasks"
        )
        assertEquals(3, results.size)
        assertEquals(Intent.ADD_TASK, results[0].intent)
        assertEquals(Intent.TOGGLE_HABIT, results[1].intent)
        assertEquals(Intent.QUERY_TASKS, results[2].intent)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Sentiment & Mood Tracker Tests
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `positive sentiment for exercise text`() {
        val score = SentimentMoodTracker.analyzeText("exercise and meditate")
        assertTrue(score > 0f)
    }

    @Test
    fun `negative sentiment for stress text`() {
        val score = SentimentMoodTracker.analyzeText("stress and crisis and problem")
        assertTrue(score < 0f)
    }

    @Test
    fun `neutral sentiment for empty text`() {
        val score = SentimentMoodTracker.analyzeText("hello world")
        assertEquals(0f, score)
    }

    @Test
    fun `momentum score in valid range`() {
        val score = SentimentMoodTracker.computeMomentum(
            habitCompletionRate = 0.8f,
            taskCompletionRate = 0.6f,
            currentMaxStreak = 10,
            avgSentiment = 0.5f
        )
        assertTrue(score in 0f..100f)
    }

    @Test
    fun `momentum label for high score`() {
        val label = SentimentMoodTracker.momentumLabel(90f)
        assertTrue(label.contains("Fire"))
    }

    @Test
    fun `momentum label for low score`() {
        val label = SentimentMoodTracker.momentumLabel(15f)
        assertTrue(label.contains("Boost"))
    }

    @Test
    fun `batch analyze returns average`() {
        val avg = SentimentMoodTracker.batchAnalyze(listOf(
            "exercise and learn",      // positive
            "stress and problem",      // negative
            "regular meeting"          // neutral
        ))
        // Should be somewhere in between
        assertTrue(avg > -1f && avg < 1f)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Behavioral Analyzer Tests
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `anomaly detection with insufficient data`() {
        val results = com.mindsetpro.ml.BehavioralAnalyzer.detectAnomalies(
            listOf("W1" to 0.8f, "W2" to 0.7f) // Only 2 weeks — not enough
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `anomaly detection catches drop off`() {
        val weeklyRates = listOf(
            "W1" to 0.85f,
            "W2" to 0.82f,
            "W3" to 0.80f,
            "W4" to 0.78f,
            "W5" to 0.20f  // significant drop
        )
        val results = com.mindsetpro.ml.BehavioralAnalyzer.detectAnomalies(weeklyRates)
        assertTrue(results.isNotEmpty())
        val anomalies = results.filter { it.isAnomaly }
        assertTrue(anomalies.any { it.weekLabel == "W5" })
    }

    @Test
    fun `cluster habits with small dataset`() {
        val features = listOf(
            com.mindsetpro.ml.BehavioralAnalyzer.HabitFeatures(
                "1", "Run", 0.9f, 0.85f, 0.8f, 15, 20, 0.9f, 0.7f, 25
            ),
            com.mindsetpro.ml.BehavioralAnalyzer.HabitFeatures(
                "2", "Read", 0.3f, 0.25f, 0.2f, 2, 5, 0.3f, 0.1f, 7
            )
        )
        // With only 2 habits and k=3, should gracefully handle
        val clusters = com.mindsetpro.ml.BehavioralAnalyzer.clusterHabits(features, k = 3)
        assertEquals(2, clusters.size)
    }
}
