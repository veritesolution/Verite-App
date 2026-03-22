package com.example.myapplication.utils

import com.example.myapplication.data.model.Intent
import com.example.myapplication.data.model.VoiceCommandResult
import com.example.myapplication.data.model.Priority
import com.example.myapplication.data.model.Category

object VoiceCommandProcessor {

    fun parse(input: String): VoiceCommandResult {
        val text = input.lowercase().trim()

        // 1. Add Task
        if (text.contains("add task") || text.contains("create task") || text.contains("new task")) {
            val entity = extractEntity(text, listOf("add task", "create task", "new task", "called", "named"))
            val priority = extractPriority(text)
            val category = extractCategory(text)
            return VoiceCommandResult(Intent.ADD_TASK, 0.9f, entity, priority, category)
        }

        // 2. Complete Task
        if (text.contains("complete task") || text.contains("finish task") || text.contains("done task")) {
            val entity = extractEntity(text, listOf("complete task", "finish task", "done task", "called", "named"))
            return VoiceCommandResult(Intent.COMPLETE_TASK, 0.8f, entity)
        }

        // 3. Delete Task
        if (text.contains("delete task") || text.contains("remove task")) {
            val entity = extractEntity(text, listOf("delete task", "remove task", "called", "named"))
            return VoiceCommandResult(Intent.DELETE_TASK, 0.8f, entity)
        }

        // 4. Add Habit
        if (text.contains("add habit") || text.contains("create habit") || text.contains("new habit")) {
            val entity = extractEntity(text, listOf("add habit", "create habit", "new habit", "called", "named"))
            return VoiceCommandResult(Intent.ADD_HABIT, 0.9f, entity)
        }

        // 5. Toggle Habit
        if (text.contains("toggle habit") || text.contains("check habit") || text.contains("habit done")) {
            val entity = extractEntity(text, listOf("toggle habit", "check habit", "habit done", "called", "named"))
            return VoiceCommandResult(Intent.TOGGLE_HABIT, 0.8f, entity)
        }

        // 6. Query Streak
        if (text.contains("streak") || text.contains("how many days")) {
            val entity = extractEntity(text, listOf("streak", "for", "habit"))
            return VoiceCommandResult(Intent.QUERY_STREAK, 0.85f, entity)
        }

        return VoiceCommandResult(Intent.UNKNOWN, 0.0f)
    }

    fun getSuggestions(): List<String> {
        return listOf(
            "Add task 'Read book' high priority",
            "Complete task 'Drink water'",
            "Add habit 'Morning run'",
            "How many days streak for 'Meditation'",
            "Delete task 'Buy milk'",
            "Toggle habit 'Reading'"
        )
    }

    private fun extractEntity(text: String, keywords: List<String>): String? {
        var result = text
        keywords.forEach { kw ->
            if (result.contains(kw)) {
                result = result.substringAfter(kw).trim()
            }
        }
        return result.split(" for ", " with ", " in ", " at ").first().trim().takeIf { it.isNotBlank() }
    }

    private fun extractPriority(text: String): String? {
        return when {
            text.contains("high") -> Priority.HIGH.label
            text.contains("medium") -> Priority.MEDIUM.label
            text.contains("low") -> Priority.LOW.label
            else -> null
        }
    }

    private fun extractCategory(text: String): String? {
        Category.entries.forEach { cat ->
            if (text.contains(cat.label.lowercase())) return cat.label
        }
        return null
    }

    // --- Legacy Verite Device Commands ---
    
    data class CommandResult(
        val action: CommandAction,
        val confidence: Float,
        val parameters: Map<String, String> = emptyMap()
    )
    
    enum class CommandAction {
        NAVIGATE_AUTO_POWER_OFF,
        NAVIGATE_HELP,
        NAVIGATE_QUICK_START,
        NAVIGATE_DEVICES,
        SET_TEMPERATURE,
        TOGGLE_VIBRATION,
        CONNECT_DEVICE,
        UNKNOWN
    }
    
    fun processCommand(input: String): CommandResult {
        val normalized = input.lowercase().trim()
        
        // Power off commands
        if (matchesAny(normalized, listOf(
            "power off", "auto power off", "power settings", 
            "turn off", "shutdown", "sleep mode", "power"
        ))) {
            return CommandResult(CommandAction.NAVIGATE_AUTO_POWER_OFF, 0.9f)
        }
        
        // Help commands
        if (matchesAny(normalized, listOf(
            "help", "feedback", "support", "assistance",
            "help and feedback", "send feedback", "get help",
            "need help"
        ))) {
            return CommandResult(CommandAction.NAVIGATE_HELP, 0.9f)
        }
        
        // Quick Start / Guide commands
        if (matchesAny(normalized, listOf(
            "guide", "quick start", "manual", "tutorial", "how to use", "instructions"
        ))) {
            return CommandResult(CommandAction.NAVIGATE_QUICK_START, 0.9f)
        }
        
        // Device commands
        if (matchesAny(normalized, listOf(
            "devices", "my devices", "show devices", 
            "device list", "connected devices", "home"
        ))) {
            return CommandResult(CommandAction.NAVIGATE_DEVICES, 0.9f)
        }
        
        // Temperature commands
        val tempPattern = Regex("(?:set |change |adjust )?temperature (?:to )?([0-9]+)")
        tempPattern.find(normalized)?.let { match ->
            val temp = match.groupValues[1]
            return CommandResult(
                CommandAction.SET_TEMPERATURE, 
                0.85f,
                mapOf("temperature" to temp)
            )
        }
        
        // Vibration commands
        if (matchesAny(normalized, listOf(
            "vibration", "vibrate", "massage", "turn on vibration",
            "start vibration", "enable vibration"
        ))) {
            return CommandResult(CommandAction.TOGGLE_VIBRATION, 0.8f)
        }
        
        // Connect device commands
        if (normalized.contains("connect") || normalized.contains("pair")) {
            return CommandResult(CommandAction.CONNECT_DEVICE, 0.7f)
        }
        
        // Unknown command
        return CommandResult(CommandAction.UNKNOWN, 0.0f)
    }
    
    private fun matchesAny(input: String, patterns: List<String>): Boolean {
        return patterns.any { pattern ->
            input.contains(pattern) || 
            calculateSimilarity(input, pattern) > 0.7
        }
    }
    
    private fun calculateSimilarity(s1: String, s2: String): Float {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0f
        
        val editDistance = levenshteinDistance(longer, shorter)
        return (longer.length - editDistance).toFloat() / longer.length
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s1[i - 1] != s2[j - 1]) {
                        newValue = minOf(minOf(newValue, lastValue), costs[j]) + 1
                    }
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
            if (i > 0) costs[s2.length] = lastValue
        }
        return costs[s2.length]
    }
}
