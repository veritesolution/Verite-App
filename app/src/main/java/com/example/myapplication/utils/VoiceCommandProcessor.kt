package com.example.myapplication.utils

object VoiceCommandProcessor {
    
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
    
    fun getSuggestions(): List<String> {
        return listOf(
            "Turn on power off",
            "Open help",
            "Show my devices",
            "Set temperature to 25",
            "Turn on vibration",
            "Connect device"
        )
    }
}
