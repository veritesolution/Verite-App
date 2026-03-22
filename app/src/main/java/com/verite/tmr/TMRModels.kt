package com.verite.tmr

import com.google.gson.annotations.SerializedName

// ─── Concept (from notebook's extract_concepts) ─────────────────────
data class Concept(
    @SerializedName("term")       val term: String,
    @SerializedName("definition") val definition: String,
    @SerializedName("mnemonic")   val mnemonic: String = "",
    @SerializedName("difficulty") val difficulty: String = "medium",
    @SerializedName("category")   val category: String = "general"
)

// ─── Flashcard (from notebook's generate_flashcards) ────────────────
data class Flashcard(
    @SerializedName("type")  val type: String,   // basic | cloze | application
    @SerializedName("front") val front: String,
    @SerializedName("back")  val back: String,
    @SerializedName("hint")  val hint: String = "",
    @SerializedName("tags")  val tags: List<String> = emptyList()
)

// ─── Quiz (from notebook's generate_quiz) ───────────────────────────
data class Quiz(
    @SerializedName("title")     val title: String = "TMR Quiz",
    @SerializedName("questions") val questions: List<QuizQuestion> = emptyList()
)

data class QuizQuestion(
    @SerializedName("type")           val type: String = "multiple_choice",
    @SerializedName("question")       val question: String,
    @SerializedName("options")        val options: List<String>,
    @SerializedName("correct_answer") val correctAnswer: Int,
    @SerializedName("explanation")    val explanation: String = ""
)

// ─── Groq API Request/Response ──────────────────────────────────────
data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    @SerializedName("max_tokens")  val maxTokens: Int = 4096,
    val temperature: Double = 0.7
)

data class GroqMessage(
    val role: String = "user",
    val content: String
)

data class GroqResponse(
    val choices: List<GroqChoice>?  // Nullable — server can return empty
)

data class GroqChoice(
    val message: GroqMessage?       // Nullable — defensive
)

// ─── Gemini API Request/Response ────────────────────────────────────
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig") val generationConfig: GeminiConfig = GeminiConfig()
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 4096
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

// ─── Session Report ─────────────────────────────────────────────────
data class TMRSessionReport(
    val version: String = "4.2.0-android",
    val timestamp: String,
    val provider: String,
    val sourceChars: Int,
    val concepts: List<Concept>,
    val cards: Int,
    val questions: Int
)
