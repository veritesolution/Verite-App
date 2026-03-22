package com.verite.tmr

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Vérité TMR v4.2 — Android Engine (Fixed)
 *
 * Replicates the full Colab pipeline:
 *   1. Concept extraction  (Groq → Gemini → keyword fallback)
 *   2. Flashcard generation
 *   3. Quiz generation
 *   4. Audio cue generation (Android TTS, properly lifecycle-managed)
 *
 * Provider cascade: Groq (14,400/day) → Gemini (250/day) → Local fallback
 *
 * ── Changes from v4.1 ──────────────────────────────────────────────
 *  - FIX: TTS rewritten with UtteranceProgressListener + suspendCancellableCoroutine
 *  - FIX: Eliminated all `!!` force-unwraps — null-safe provider calls
 *  - FIX: Exponential backoff on 429 rate limits (up to 2 retries per provider)
 *  - FIX: Error response body logged for debugging
 *  - FIX: Renamed tfidfFallbackConcepts → keywordFallbackConcepts (accurate name)
 *  - FIX: Robust JSON extraction with regex-first, bracket-match fallback
 *  - FIX: Provider field no longer crashes when only fallback is available
 *  - FIX: applicationContext used for TTS to prevent Activity leak
 */
class TMREngine(
    private val groqApiKey: String? = null,
    private val geminiApiKey: String? = null
) {
    companion object {
        private const val TAG = "TMREngine"
        private const val GROQ_BASE_URL = "https://api.groq.com/openai/v1/"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"
        private const val MAX_RETRIES = 2
        private const val INITIAL_BACKOFF_MS = 1000L
    }

    private val gson = Gson()
    private val providers = mutableListOf<String>()

    // ─── Retrofit Clients ────────────────────────────────────────────
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // FIX: Null-safe initialization — no `!!` anywhere downstream
    private val groqApi: GroqApiService? = if (!groqApiKey.isNullOrBlank()) {
        providers.add("groq")
        Retrofit.Builder()
            .baseUrl(GROQ_BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    } else null

    private val geminiApi: GeminiApiService? = if (!geminiApiKey.isNullOrBlank()) {
        providers.add("gemini")
        Retrofit.Builder()
            .baseUrl(GEMINI_BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    } else null

    init {
        providers.add("fallback") // Always available
        Log.d(TAG, "Providers: ${providers.joinToString(" → ")}")
    }

    val availableProviders: List<String> get() = providers.toList()

    // ═════════════════════════════════════════════════════════════════
    //  PROVIDER CASCADE — with exponential backoff on rate limits
    // ═════════════════════════════════════════════════════════════════

    private suspend fun llmCall(prompt: String): Pair<String?, String> =
        withContext(Dispatchers.IO) {
            for (provider in providers) {
                if (provider == "fallback") return@withContext null to "fallback"

                // FIX: Retry with exponential backoff on 429
                var retries = 0
                while (retries <= MAX_RETRIES) {
                    try {
                        val result = callProvider(provider, prompt)
                        if (result != null) return@withContext result to provider

                        // Non-429 failure — skip to next provider immediately
                        break
                    } catch (e: RateLimitException) {
                        retries++
                        if (retries <= MAX_RETRIES) {
                            val backoff = INITIAL_BACKOFF_MS * (1L shl (retries - 1))
                            Log.w(TAG, "$provider rate limited — retrying in ${backoff}ms (attempt $retries/$MAX_RETRIES)")
                            delay(backoff)
                        } else {
                            Log.w(TAG, "$provider rate limited — exhausted retries, trying next provider")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "$provider failed: ${e.message}")
                        break // Non-retryable error — next provider
                    }
                }
            }
            null to "fallback"
        }

    /** Internal exception to signal retryable 429 errors. */
    private class RateLimitException(message: String) : Exception(message)

    /**
     * Call a single provider. Returns response text on success, null on non-retryable failure.
     * Throws [RateLimitException] on 429 to trigger retry logic.
     */
    private suspend fun callProvider(provider: String, prompt: String): String? {
        return when (provider) {
            "groq" -> {
                // FIX: Null-safe — skip if API not initialized
                val api = groqApi ?: return null
                val key = groqApiKey ?: return null

                val request = GroqRequest(messages = listOf(GroqMessage(content = prompt)))
                val response = api.chatCompletion(authHeader = "Bearer $key", request = request)
                handleResponse(response, provider) {
                    it.choices?.firstOrNull()?.message?.content
                }
            }

            "gemini" -> {
                val api = geminiApi ?: return null
                val key = geminiApiKey ?: return null

                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                )
                val response = api.generateContent(apiKey = key, request = request)
                handleResponse(response, provider) {
                    it.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }
            }

            else -> null
        }
    }

    /**
     * Unified response handler. Extracts text on success, logs error body on failure,
     * throws [RateLimitException] on 429.
     */
    private fun <T> handleResponse(
        response: Response<T>,
        provider: String,
        extractor: (T) -> String?
    ): String? {
        if (response.isSuccessful) {
            val body = response.body() ?: return null
            val text = extractor(body)
            if (!text.isNullOrBlank()) {
                Log.d(TAG, "✅ $provider responded (${text.length} chars)")
                return text
            }
            return null
        }

        val code = response.code()
        // FIX: Log the error body for debugging (was silently ignored before)
        val errorBody = try { response.errorBody()?.string()?.take(200) } catch (_: Exception) { null }
        Log.w(TAG, "$provider HTTP $code: ${errorBody ?: "no error body"}")

        if (code == 429) throw RateLimitException("$provider rate limited (429)")
        return null // Non-retryable HTTP error
    }

    // ═════════════════════════════════════════════════════════════════
    //  CONTENT TYPE DETECTION — Same as notebook's _content_type()
    // ═════════════════════════════════════════════════════════════════

    private fun detectContentType(text: String): String {
        val t = text.lowercase().take(3000)
        return when {
            listOf("hypothesis", "abstract", "methodology", "findings").any { it in t } -> "academic"
            listOf("revenue", "pricing", "business plan", "market", "proposal", "profit").any { it in t } -> "business"
            listOf("function", "algorithm", "api", "class ", "import ", "def ").any { it in t } -> "technical"
            listOf("patient", "diagnosis", "treatment", "clinical").any { it in t } -> "medical"
            else -> "general"
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  EXTRACT CONCEPTS — Same prompt as notebook Cell 5
    // ═════════════════════════════════════════════════════════════════

    suspend fun extractConcepts(text: String, maxConcepts: Int = 10): List<Concept> {
        val contentType = detectContentType(text)
        val textInput = text.take(200_000)

        val prompt = """You are an expert educator. Document type: $contentType.
Extract the $maxConcepts most important concepts for study and memory retention.
Extract MEANINGFUL concepts — key ideas, principles, terms, relationships.
NOT generic single words.

Return ONLY a valid JSON array. No markdown fences, no explanation:
[{"term":"concept name (2-6 words)","definition":"clear 1-3 sentence explanation","mnemonic":"memorable phrase or acronym","difficulty":"easy|medium|hard","category":"specific topic"}]

DOCUMENT:
$textInput

JSON:"""

        val (raw, provider) = llmCall(prompt)

        if (raw != null && provider != "fallback") {
            try {
                val concepts = parseJsonArray<Concept>(raw)
                val valid = concepts.filter {
                    it.term.length > 2 && it.definition.length > 20
                }.take(maxConcepts)

                if (valid.isNotEmpty()) {
                    Log.d(TAG, "✅ ${valid.size} concepts via $provider")
                    return valid
                }
            } catch (e: Exception) {
                Log.w(TAG, "JSON parse failed: ${e.message}")
            }
        }

        // Keyword fallback (simplified for Android)
        return keywordFallbackConcepts(text, maxConcepts)
    }

    // ═════════════════════════════════════════════════════════════════
    //  GENERATE FLASHCARDS — Same prompt as notebook Cell 5
    // ═════════════════════════════════════════════════════════════════

    suspend fun generateFlashcards(concepts: List<Concept>): List<Flashcard> {
        if (concepts.isEmpty()) return emptyList()

        val conceptsJson = gson.toJson(concepts.take(12))
        val prompt = """Create Anki flashcards. Varied types: basic, cloze, application.
Return ONLY JSON array:
[{"type":"basic|cloze|application","front":"question","back":"answer","hint":"optional","tags":["t1"]}]

CONCEPTS:
$conceptsJson
JSON:"""

        val (raw, provider) = llmCall(prompt)

        if (raw != null && provider != "fallback") {
            try {
                val cards = parseJsonArray<Flashcard>(raw)
                    .filter { it.front.isNotBlank() && it.back.isNotBlank() }
                if (cards.isNotEmpty()) {
                    Log.d(TAG, "✅ ${cards.size} flashcards via $provider")
                    return cards
                }
            } catch (e: Exception) {
                Log.w(TAG, "Flashcard parse failed: ${e.message}")
            }
        }

        // Fallback — same as notebook's _fb_flashcards()
        return concepts.flatMap { c ->
            listOf(
                Flashcard("basic", "Define: ${c.term}", c.definition, c.mnemonic, listOf(c.category)),
                Flashcard("cloze", "_______ — ${c.definition.take(100)}...", c.term, "", listOf(c.category)),
                Flashcard("application", "Why is '${c.term}' important?", c.definition, "", listOf(c.category))
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  GENERATE QUIZ — Same prompt as notebook Cell 5
    // ═════════════════════════════════════════════════════════════════

    suspend fun generateQuiz(concepts: List<Concept>, numQuestions: Int = 5): Quiz {
        if (concepts.isEmpty()) return Quiz()

        val conceptsJson = gson.toJson(concepts.take(12))
        val prompt = """Create $numQuestions-question MCQ quiz. 4 options, one correct. Varied types.
Return ONLY JSON:
{"title":"Quiz Title","questions":[{"type":"multiple_choice","question":"text","options":["A. x","B. y","C. z","D. w"],"correct_answer":0,"explanation":"why"}]}

CONCEPTS:
$conceptsJson
JSON:"""

        val (raw, provider) = llmCall(prompt)

        if (raw != null && provider != "fallback") {
            try {
                val quiz = parseJsonObject<Quiz>(raw)
                if (quiz.questions.isNotEmpty()) {
                    Log.d(TAG, "✅ ${quiz.questions.size} questions via $provider")
                    return quiz
                }
            } catch (e: Exception) {
                Log.w(TAG, "Quiz parse failed: ${e.message}")
            }
        }

        // Fallback — same as notebook's _fb_quiz()
        return fallbackQuiz(concepts, numQuestions)
    }

    // ═════════════════════════════════════════════════════════════════
    //  AUDIO CUE GENERATION — Completely rewritten
    //
    //  FIX: Previous version had multiple critical bugs:
    //    1. Created TTS but then tried to get a *different* instance
    //       from getSystemService (which doesn't work that way)
    //    2. Called onComplete immediately after the loop, before any
    //       synthesizeToFile calls had finished (they're async)
    //    3. Never called tts.shutdown() — resource leak
    //    4. Passed Activity context — potential memory leak
    //
    //  Now: Uses suspendCancellableCoroutine + UtteranceProgressListener
    //  to properly await each synthesis, then shuts down TTS.
    // ═════════════════════════════════════════════════════════════════

    /**
     * Generate TMR audio cues as WAV files using Android TTS.
     *
     * This is now a suspend function that properly awaits all synthesis
     * operations before returning. Call from a coroutine scope.
     *
     * @param context Use applicationContext to avoid Activity leaks.
     * @param concepts The concepts to generate audio for.
     * @param outputDir Directory to write WAV files into.
     * @param onProgress Called after each cue is synthesized: (completed, total).
     * @return List of generated audio files.
     */
    suspend fun generateAudioCues(
        context: Context,
        concepts: List<Concept>,
        outputDir: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<File> = withContext(Dispatchers.Main) {
        // FIX: Use applicationContext to prevent Activity leak
        val appContext = context.applicationContext
        if (!outputDir.exists()) outputDir.mkdirs()

        // Step 1: Initialize TTS and wait for it to be ready
        val tts = initTts(appContext) ?: run {
            Log.e(TAG, "TTS initialization failed")
            return@withContext emptyList()
        }

        try {
            // Configure for sleep cues
            tts.language = Locale.US
            tts.setSpeechRate(0.75f)  // Slower for sleep cues
            tts.setPitch(0.9f)        // Slightly lower pitch

            val files = mutableListOf<File>()

            // Step 2: Synthesize each concept, awaiting completion one at a time
            concepts.forEachIndexed { index, concept ->
                val cueText = "${concept.term}. ${concept.definition.take(150)}"
                val file = File(outputDir, "cue_${index + 1}_${sanitize(concept.term)}.wav")

                val success = synthesizeAndAwait(tts, cueText, file, "tmr_cue_$index")
                if (success) {
                    files.add(file)
                    Log.d(TAG, "🎵 Cue ${index + 1}/${concepts.size}: ${concept.term}")
                } else {
                    Log.w(TAG, "⚠️ Failed to synthesize cue ${index + 1}: ${concept.term}")
                }
                onProgress(index + 1, concepts.size)
            }

            Log.d(TAG, "✅ ${files.size}/${concepts.size} audio cues generated")
            files
        } finally {
            // Step 3: Always shut down TTS to prevent resource leaks
            tts.shutdown()
            Log.d(TAG, "TTS shutdown complete")
        }
    }

    /**
     * Initialize TTS engine and suspend until ready.
     * Returns null if initialization fails.
     */
    private suspend fun initTts(context: Context): TextToSpeech? =
        suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    continuation.resume(tts)
                } else {
                    continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation {
                tts?.shutdown()
            }
        }

    /**
     * Synthesize a single utterance to file and suspend until complete.
     * Returns true on success, false on failure.
     */
    private suspend fun synthesizeAndAwait(
        tts: TextToSpeech,
        text: String,
        outputFile: File,
        utteranceId: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    safeResume(continuation, true)
                }
            }

            @Deprecated("Deprecated in API")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    Log.e(TAG, "TTS error for utterance: $id")
                    safeResume(continuation, false)
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    Log.e(TAG, "TTS error for utterance $id, code: $errorCode")
                    safeResume(continuation, false)
                }
            }
        })

        @Suppress("DEPRECATION")
        val result = tts.synthesizeToFile(text, null, outputFile, utteranceId)
        if (result == TextToSpeech.ERROR) {
            safeResume(continuation, false)
        }
    }

    /** Resume a continuation safely — guards against double-resume from race conditions. */
    private fun <T> safeResume(continuation: CancellableContinuation<T>, value: T) {
        if (continuation.isActive) continuation.resume(value)
    }

    // ═════════════════════════════════════════════════════════════════
    //  FULL PIPELINE — Run everything in sequence
    // ═════════════════════════════════════════════════════════════════

    data class TMRResult(
        val concepts: List<Concept>,
        val flashcards: List<Flashcard>,
        val quiz: Quiz,
        val provider: String,
        val sourceChars: Int
    )

    suspend fun runFullPipeline(text: String): TMRResult {
        Log.d(TAG, "🚀 Starting TMR pipeline (${text.length} chars)")

        // Step 1: Extract concepts
        val concepts = extractConcepts(text)
        Log.d(TAG, "📊 ${concepts.size} concepts extracted")

        // Step 2: Generate flashcards
        val flashcards = generateFlashcards(concepts)
        Log.d(TAG, "🃏 ${flashcards.size} flashcards generated")

        // Step 3: Generate quiz
        val quiz = generateQuiz(concepts)
        Log.d(TAG, "❓ ${quiz.questions.size} quiz questions generated")

        // FIX: Previously crashed if only "fallback" was in providers list
        val usedProvider = providers.firstOrNull { it != "fallback" } ?: "fallback"

        return TMRResult(
            concepts = concepts,
            flashcards = flashcards,
            quiz = quiz,
            provider = usedProvider,
            sourceChars = text.length
        )
    }

    // ═════════════════════════════════════════════════════════════════
    //  EXPORT — JSON files matching notebook output format
    // ═════════════════════════════════════════════════════════════════

    fun exportToJson(result: TMRResult, outputDir: File): Map<String, File> {
        if (!outputDir.exists()) outputDir.mkdirs()
        val files = mutableMapOf<String, File>()

        // Flashcards JSON
        val flashcardsFile = File(outputDir, "tmr_flashcards.json")
        flashcardsFile.writeText(gson.toJson(result.flashcards))
        files["flashcards"] = flashcardsFile

        // Flashcards TSV (Anki import format)
        val ankiFile = File(outputDir, "tmr_flashcards_anki.tsv")
        ankiFile.writeText(
            result.flashcards.joinToString("\n") { "${it.front}\t${it.back}\t${it.tags.joinToString(",")}" }
        )
        files["anki_tsv"] = ankiFile

        // Quiz JSON
        val quizFile = File(outputDir, "tmr_quiz.json")
        quizFile.writeText(gson.toJson(result.quiz))
        files["quiz"] = quizFile

        // Session report
        val reportFile = File(outputDir, "session_report.json")
        val report = TMRSessionReport(
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
            provider = result.provider,
            sourceChars = result.sourceChars,
            concepts = result.concepts,
            cards = result.flashcards.size,
            questions = result.quiz.questions.size
        )
        reportFile.writeText(gson.toJson(report))
        files["report"] = reportFile

        return files
    }

    // ═════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════

    /**
     * FIX: More robust JSON extraction.
     * Strategy: try regex for fenced blocks first, then bracket-matching fallback.
     */
    private inline fun <reified T> parseJsonArray(raw: String): List<T> {
        val jsonStr = extractJsonBlock(raw, '[', ']')
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        return gson.fromJson(jsonStr, type)
    }

    private inline fun <reified T> parseJsonObject(raw: String): T {
        val jsonStr = extractJsonBlock(raw, '{', '}')
        return gson.fromJson(jsonStr, T::class.java)
    }

    /**
     * Extract JSON from LLM output. Handles:
     *  - ```json ... ``` fenced blocks
     *  - Raw JSON with surrounding text
     *  - Multiple JSON blocks (takes the first complete one)
     */
    private fun extractJsonBlock(raw: String, open: Char, close: Char): String {
        // Strategy 1: Try to extract from markdown fences
        val fencePattern = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")
        val fenceMatch = fencePattern.find(raw)
        if (fenceMatch != null) {
            val inner = fenceMatch.groupValues[1].trim()
            if (inner.startsWith(open.toString())) {
                try {
                    // Validate it's actually parseable before returning
                    gson.fromJson<Any>(inner, Any::class.java)
                    return inner
                } catch (_: Exception) { /* fall through */ }
            }
        }

        // Strategy 2: Find first matching open bracket, then find its close
        val cleaned = raw
            .replace(Regex("```(?:json)?"), "")
            .replace("```", "")
            .trim()

        val start = cleaned.indexOf(open)
        if (start == -1) throw IllegalArgumentException("No '$open' found in response")

        // Walk forward to find the matching close bracket
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until cleaned.length) {
            val ch = cleaned[i]
            if (escaped) { escaped = false; continue }
            if (ch == '\\') { escaped = true; continue }
            if (ch == '"') { inString = !inString; continue }
            if (inString) continue
            if (ch == open) depth++
            else if (ch == close) {
                depth--
                if (depth == 0) return cleaned.substring(start, i + 1)
            }
        }

        // Last resort: take from start to last occurrence of close bracket
        val end = cleaned.lastIndexOf(close)
        if (end > start) return cleaned.substring(start, end + 1)

        throw IllegalArgumentException("Could not extract JSON block from response")
    }

    /**
     * FIX: Renamed from tfidfFallbackConcepts — this is keyword frequency,
     * not TF-IDF. The name was misleading since it doesn't use inverse
     * document frequency weighting like scikit-learn's TfidfVectorizer.
     */
    private fun keywordFallbackConcepts(text: String, n: Int): List<Concept> {
        Log.d(TAG, "📊 Using local keyword extraction fallback...")

        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "have", "has", "had",
            "do", "does", "did", "will", "would", "could", "should", "this", "that",
            "and", "but", "or", "in", "on", "at", "to", "for", "of", "with", "by",
            "from", "not", "its", "our", "all", "can", "than", "so", "been", "being",
            "they", "them", "their", "there", "then", "also", "just", "more", "some",
            "such", "about", "over", "into", "only", "other", "very", "when", "what",
            "which", "while", "each", "most", "both", "through", "between", "after",
            "before", "during", "without", "within", "along", "every", "where", "much"
        )

        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.trim().length > 40 }

        val words = Regex("\\b[a-zA-Z]{4,}\\b")
            .findAll(text.lowercase())
            .map { it.value }
            .filter { it !in stopWords }
            .toList()

        val wordFreq = words.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }

        val concepts = mutableListOf<Concept>()
        val usedSentences = mutableSetOf<Int>()

        for ((term, _) in wordFreq) {
            if (concepts.size >= n) break
            val sentIdx = sentences.indexOfFirst { sent ->
                term in sent.lowercase() && sentences.indexOf(sent) !in usedSentences
            }
            if (sentIdx >= 0) {
                usedSentences.add(sentIdx)
                concepts.add(
                    Concept(
                        term = term.replaceFirstChar { it.uppercase() },
                        definition = sentences[sentIdx].take(200),
                        mnemonic = term.take(4).uppercase(),
                        difficulty = "medium",
                        category = "extracted (local)"
                    )
                )
            }
        }
        return concepts
    }

    private fun fallbackQuiz(concepts: List<Concept>, numQ: Int): Quiz {
        val questions = concepts.take(numQ).map { c ->
            val wrongAnswers = concepts
                .filter { it.term != c.term }
                .map { it.term }
                .take(3)
                .toMutableList()
            while (wrongAnswers.size < 3) wrongAnswers.add("Unrelated ${wrongAnswers.size + 1}")

            val options = (listOf(c.term) + wrongAnswers).shuffled()
            val correctIdx = options.indexOf(c.term)

            QuizQuestion(
                question = "Which matches: '${c.definition.take(120)}'?",
                options = options.mapIndexed { i, o -> "${('A' + i)}. $o" },
                correctAnswer = correctIdx,
                explanation = c.mnemonic
            )
        }
        return Quiz(title = "TMR Study Quiz", questions = questions)
    }

    private fun sanitize(text: String): String =
        text.lowercase().replace(Regex("[^a-zA-Z0-9]"), "_").take(40)
}
