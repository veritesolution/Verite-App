package com.example.myapplication.utils

object TextCleaningUtils {
    
    /**
     * Cleans AI-generated text by removing formatting artifacts
     * - Removes hashtags (#)
     * - Removes excessive quotation marks
     * - Removes markdown formatting (**, *, etc.)
     * - Cleans up extra whitespace
     * - Formats bullet points properly
     */
    fun cleanAiText(text: String): String {
        return text
            // Remove hashtags
            .replace("#", "")
            // Remove markdown bold
            .replace("**", "")
            // Remove markdown italic
            .replace("*", "")
            // Remove excessive quotes (more than 2 in a row)
            .replace(Regex("[\"\u201C\u201D]{2,}"), "")
            // Clean up bullet points - replace various bullet formats with standard dash
            .replace(Regex("^[•●○◦▪▫]\\s*", RegexOption.MULTILINE), "- ")
            // Remove multiple spaces
            .replace(Regex("\\s{2,}"), " ")
            // Remove spaces before punctuation
            .replace(Regex("\\s+([.,!?;:])"), "$1")
            // Trim each line
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }
    
    /**
     * Extracts a clean suggestion from AI text
     * Looks for suggestion-related content and formats it nicely
     */
    fun extractSuggestion(fullPlanText: String, day: Int): String {
        // Try to find day-specific suggestions
        val dayPattern = Regex("day\\s*$day[:\\s-]+(.*?)(?=day\\s*${day + 1}|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val dayMatch = dayPattern.find(fullPlanText)
        
        return if (dayMatch != null && dayMatch.groups.size > 1) {
            val matchedText = dayMatch.groups[1]?.value ?: ""
            cleanAiText(matchedText.take(200))
        } else {
            // Fallback: extract first meaningful paragraph
            val paragraphs = fullPlanText.split("\n\n")
            val suggestion = paragraphs.firstOrNull { it.length > 50 } ?: fullPlanText.take(200)
            cleanAiText(suggestion)
        }
    }
    
    /**
     * Formats time in minutes to a readable string
     */
    fun formatTime(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }
}
