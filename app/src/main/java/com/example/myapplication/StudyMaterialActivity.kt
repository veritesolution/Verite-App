package com.example.myapplication

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.util.VeriteLogoHelper
import com.verite.tmr.*
import kotlinx.coroutines.launch
import com.example.myapplication.ui.components.VeriteAlert

class StudyMaterialActivity : AppCompatActivity() {

    private lateinit var processingLayout: LinearLayout
    private lateinit var resultsLayout: LinearLayout
    private lateinit var tvProcessingStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var contentContainer: LinearLayout

    private lateinit var tabConcepts: TextView
    private lateinit var tabFlashcards: TextView
    private lateinit var tabQuiz: TextView

    private var concepts: List<Concept> = emptyList()
    private var flashcards: List<Flashcard> = emptyList()
    private var quiz: Quiz = Quiz()

    // Flashcard state
    private var currentFlashcardIndex = 0
    private var showingFront = true

    // Quiz state
    private var selectedAnswers = mutableMapOf<Int, Int>()
    private var quizSubmitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_material)

        initViews()
        setupHeader()
        setupTabs()

        val text = intent.getStringExtra("EXTRA_TEXT") ?: ""
        val initialTab = intent.getStringExtra("INITIAL_TAB") ?: "concepts"

        if (text.isNotBlank()) {
            runPipeline(text, initialTab)
        } else {
            tvProcessingStatus.text = "No document text provided"
        }
    }

    private fun initViews() {
        processingLayout = findViewById(R.id.processingLayout)
        resultsLayout = findViewById(R.id.resultsLayout)
        tvProcessingStatus = findViewById(R.id.tvProcessingStatus)
        progressBar = findViewById(R.id.progressBar)
        contentContainer = findViewById(R.id.contentContainer)
        tabConcepts = findViewById(R.id.tabConcepts)
        tabFlashcards = findViewById(R.id.tabFlashcards)
        tabQuiz = findViewById(R.id.tabQuiz)
    }

    private fun setupHeader() {
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.profileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        VeriteLogoHelper.applyLogoStyle(findViewById(R.id.headerTitle))
    }

    private fun setupTabs() {
        tabConcepts.setOnClickListener { selectTab("concepts") }
        tabFlashcards.setOnClickListener { selectTab("flashcards") }
        tabQuiz.setOnClickListener { selectTab("quiz") }
    }

    private fun selectTab(tab: String) {
        // Reset all tab styles
        listOf(tabConcepts, tabFlashcards, tabQuiz).forEach {
            it.setTextColor(Color.parseColor("#80FFFFFF"))
        }

        // Highlight active tab
        when (tab) {
            "concepts" -> {
                tabConcepts.setTextColor(Color.parseColor("#00BFA5"))
                showConcepts()
            }
            "flashcards" -> {
                tabFlashcards.setTextColor(Color.parseColor("#00BFA5"))
                showFlashcards()
            }
            "quiz" -> {
                tabQuiz.setTextColor(Color.parseColor("#00BFA5"))
                showQuiz()
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  PIPELINE
    // ═════════════════════════════════════════════════════════════════

    private fun runPipeline(text: String, initialTab: String) {
        processingLayout.visibility = View.VISIBLE
        resultsLayout.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Step 1: Extract concepts
                updateProgress(10, "Extracting key concepts...")
                val engine = TMREngine()
                concepts = engine.extractConcepts(text)
                updateProgress(40, "Generating flashcards...")

                // Step 2: Generate flashcards
                flashcards = engine.generateFlashcards(concepts)
                updateProgress(70, "Creating quiz questions...")

                // Step 3: Generate quiz
                quiz = engine.generateQuiz(concepts)
                updateProgress(100, "Complete!")

                // Show results
                showResults(initialTab)

            } catch (e: Exception) {
                tvProcessingStatus.text = "Error: ${e.message}"
                progressBar.progress = 0
            }
        }
    }

    private fun updateProgress(percent: Int, status: String) {
        tvProcessingStatus.text = status
        ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, percent).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun showResults(initialTab: String) {
        // Update stats
        findViewById<TextView>(R.id.tvConceptCount).text = "${concepts.size}"
        findViewById<TextView>(R.id.tvFlashcardCount).text = "${flashcards.size}"
        findViewById<TextView>(R.id.tvQuizCount).text = "${quiz.questions.size}"

        // Transition
        processingLayout.visibility = View.GONE
        resultsLayout.visibility = View.VISIBLE
        resultsLayout.alpha = 0f
        resultsLayout.animate().alpha(1f).setDuration(300).start()

        selectTab(initialTab)
    }

    // ═════════════════════════════════════════════════════════════════
    //  CONCEPTS TAB
    // ═════════════════════════════════════════════════════════════════

    private fun showConcepts() {
        contentContainer.removeAllViews()
        if (concepts.isEmpty()) {
            addEmptyState("No concepts extracted")
            return
        }

        concepts.forEachIndexed { index, concept ->
            val card = createConceptCard(concept, index + 1)
            contentContainer.addView(card)
        }
    }

    private fun createConceptCard(concept: Concept, number: Int): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) }
            radius = dpToPx(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#0C1917"))
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
        }

        // Header row: number + term + difficulty badge
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val numberBadge = TextView(this).apply {
            text = "$number"
            setTextColor(Color.parseColor("#071211"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00BFA5"))
                setSize(dpToPx(28), dpToPx(28))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                marginEnd = dpToPx(12)
            }
        }

        val termText = TextView(this).apply {
            text = concept.term
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val diffBadge = TextView(this).apply {
            text = concept.difficulty.uppercase()
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            val badgeColor = when (concept.difficulty.lowercase()) {
                "easy" -> "#1B5E20"
                "hard" -> "#B71C1C"
                else -> "#E65100"
            }
            val textCol = when (concept.difficulty.lowercase()) {
                "easy" -> "#4CAF50"
                "hard" -> "#EF5350"
                else -> "#FF9800"
            }
            setTextColor(Color.parseColor(textCol))
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor(badgeColor))
            }
            background = bg
        }

        headerRow.addView(numberBadge)
        headerRow.addView(termText)
        headerRow.addView(diffBadge)

        // Definition
        val defText = TextView(this).apply {
            text = concept.definition
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(10) }
            setLineSpacing(dpToPx(3).toFloat(), 1f)
        }

        // Mnemonic (if present)
        inner.addView(headerRow)
        inner.addView(defText)

        if (concept.mnemonic.isNotBlank()) {
            val mnemonicRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(8) }
            }
            val lightBulb = TextView(this).apply {
                text = "💡 "
                textSize = 14f
            }
            val mnemonicText = TextView(this).apply {
                text = concept.mnemonic
                setTextColor(Color.parseColor("#00BFA5"))
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            mnemonicRow.addView(lightBulb)
            mnemonicRow.addView(mnemonicText)
            inner.addView(mnemonicRow)
        }

        card.addView(inner)
        return card
    }

    // ═════════════════════════════════════════════════════════════════
    //  FLASHCARDS TAB
    // ═════════════════════════════════════════════════════════════════

    private fun showFlashcards() {
        contentContainer.removeAllViews()
        if (flashcards.isEmpty()) {
            addEmptyState("No flashcards generated")
            return
        }

        currentFlashcardIndex = 0
        showingFront = true
        buildFlashcardUI()
    }

    private fun buildFlashcardUI() {
        contentContainer.removeAllViews()
        val fc = flashcards[currentFlashcardIndex]

        // Counter
        val counter = TextView(this).apply {
            text = "Card ${currentFlashcardIndex + 1} of ${flashcards.size}"
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) }
        }
        contentContainer.addView(counter)

        // Card type badge
        val typeBadge = TextView(this).apply {
            text = fc.type.uppercase()
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00BFA5"))
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00BFA5"))
                setColor(Color.parseColor("#0D2420"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(12)
            }
        }
        contentContainer.addView(typeBadge)

        // The flashcard itself
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(260)
            ).apply { bottomMargin = dpToPx(16) }
            radius = dpToPx(20).toFloat()
            cardElevation = dpToPx(4).toFloat()
            setCardBackgroundColor(if (showingFront) Color.parseColor("#0D2420") else Color.parseColor("#1A3D36"))
        }

        val cardInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(28), dpToPx(24), dpToPx(28), dpToPx(24))
        }

        val sideLabel = TextView(this).apply {
            text = if (showingFront) "FRONT" else "BACK"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#4DB6AC"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) }
        }

        val cardText = TextView(this).apply {
            text = if (showingFront) fc.front else fc.back
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            setLineSpacing(dpToPx(4).toFloat(), 1f)
        }

        cardInner.addView(sideLabel)
        cardInner.addView(cardText)

        // Hint (front side only)
        if (showingFront && fc.hint.isNotBlank()) {
            val hintText = TextView(this).apply {
                text = "💡 Hint: ${fc.hint}"
                setTextColor(Color.parseColor("#80FFFFFF"))
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(12) }
            }
            cardInner.addView(hintText)
        }

        card.addView(cardInner)

        // Tap to flip
        card.setOnClickListener {
            showingFront = !showingFront
            card.animate().rotationY(90f).setDuration(150).withEndAction {
                buildFlashcardUI()
                contentContainer.findViewWithTag<View>("flashcard_main")?.let { v ->
                    v.rotationY = -90f
                    v.animate().rotationY(0f).setDuration(150).start()
                }
            }.start()
        }
        card.tag = "flashcard_main"
        contentContainer.addView(card)

        // Tap instruction
        val tapHint = TextView(this).apply {
            text = "Tap card to flip"
            setTextColor(Color.parseColor("#4DB6AC"))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(20) }
        }
        contentContainer.addView(tapHint)

        // Navigation buttons
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val prevBtn = createNavButton("← Previous") {
            if (currentFlashcardIndex > 0) {
                currentFlashcardIndex--
                showingFront = true
                buildFlashcardUI()
            }
        }
        prevBtn.alpha = if (currentFlashcardIndex > 0) 1f else 0.3f

        val nextBtn = createNavButton("Next →") {
            if (currentFlashcardIndex < flashcards.size - 1) {
                currentFlashcardIndex++
                showingFront = true
                buildFlashcardUI()
            }
        }
        nextBtn.alpha = if (currentFlashcardIndex < flashcards.size - 1) 1f else 0.3f

        navRow.addView(prevBtn)
        navRow.addView(nextBtn)
        contentContainer.addView(navRow)
    }

    private fun createNavButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12))
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#0D2420"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dpToPx(8); marginStart = dpToPx(8) }
            setOnClickListener { onClick() }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  QUIZ TAB
    // ═════════════════════════════════════════════════════════════════

    private fun showQuiz() {
        contentContainer.removeAllViews()
        selectedAnswers.clear()
        quizSubmitted = false

        if (quiz.questions.isEmpty()) {
            addEmptyState("No quiz questions generated")
            return
        }

        // Title
        val title = TextView(this).apply {
            text = quiz.title
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) }
        }
        contentContainer.addView(title)

        quiz.questions.forEachIndexed { qIndex, question ->
            val qCard = createQuizQuestionCard(question, qIndex)
            contentContainer.addView(qCard)
        }

        // Submit button
        val submitBtn = TextView(this).apply {
            text = "Submit Quiz"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#071211"))
            gravity = Gravity.CENTER
            setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14))
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#00BFA5"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(8) }
            setOnClickListener { submitQuiz() }
        }
        contentContainer.addView(submitBtn)
    }

    private fun createQuizQuestionCard(question: QuizQuestion, qIndex: Int): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) }
            radius = dpToPx(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#0C1917"))
            tag = "quiz_card_$qIndex"
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
        }

        // Question number + text
        val qText = TextView(this).apply {
            text = "Q${qIndex + 1}. ${question.question}"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dpToPx(3).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) }
        }
        inner.addView(qText)

        // Options
        val radioGroup = RadioGroup(this).apply {
            tag = "radio_$qIndex"
        }

        question.options.forEachIndexed { oIndex, option ->
            val rb = RadioButton(this).apply {
                text = option
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 14f
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5"))
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                id = View.generateViewId()
                tag = "option_${qIndex}_$oIndex"
            }
            radioGroup.addView(rb)
        }

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            val optTag = rb?.tag?.toString() ?: return@setOnCheckedChangeListener
            val optIndex = optTag.split("_").lastOrNull()?.toIntOrNull() ?: return@setOnCheckedChangeListener
            selectedAnswers[qIndex] = optIndex
        }

        inner.addView(radioGroup)

        // Explanation (hidden until submit)
        val explanationView = TextView(this).apply {
            visibility = View.GONE
            textSize = 13f
            setLineSpacing(dpToPx(2).toFloat(), 1f)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            tag = "explanation_$qIndex"
        }
        inner.addView(explanationView)

        card.addView(inner)
        return card
    }

    private fun submitQuiz() {
        if (quizSubmitted) return
        quizSubmitted = true

        var correct = 0
        quiz.questions.forEachIndexed { qIndex, question ->
            val selected = selectedAnswers[qIndex]
            val isCorrect = selected == question.correctAnswer

            if (isCorrect) correct++

            // Show explanation
            val explanation = contentContainer.findViewWithTag<TextView>("explanation_$qIndex")
            explanation?.apply {
                visibility = View.VISIBLE
                text = if (isCorrect) {
                    "✅ Correct! ${question.explanation}"
                } else {
                    "❌ Incorrect. The answer is: ${question.options.getOrElse(question.correctAnswer) { "?" }}\n${question.explanation}"
                }
                setTextColor(if (isCorrect) Color.parseColor("#4CAF50") else Color.parseColor("#EF5350"))
                val bg = GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(if (isCorrect) Color.parseColor("#0D2E0D") else Color.parseColor("#2E0D0D"))
                }
                background = bg
            }

            // Update card background
            val card = contentContainer.findViewWithTag<CardView>("quiz_card_$qIndex")
            card?.setCardBackgroundColor(
                if (isCorrect) Color.parseColor("#0D2420") else Color.parseColor("#1A0D0D")
            )
        }

        // Show score at top
        val scoreCard = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) }
            radius = dpToPx(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#0D2420"))
        }
        val scoreInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
        }
        val scoreText = TextView(this).apply {
            text = "Score: $correct / ${quiz.questions.size}"
            setTextColor(Color.parseColor("#00BFA5"))
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val percentText = TextView(this).apply {
            val pct = if (quiz.questions.isNotEmpty()) (correct * 100 / quiz.questions.size) else 0
            text = "${pct}% correct"
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
        }
        scoreInner.addView(scoreText)
        scoreInner.addView(percentText)
        scoreCard.addView(scoreInner)
        contentContainer.addView(scoreCard, 1) // Insert after title

        VeriteAlert.success(this, "Score: $correct/${quiz.questions.size}")
    }

    // ═════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════

    private fun addEmptyState(msg: String) {
        val tv = TextView(this).apply {
            text = msg
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(60) }
        }
        contentContainer.addView(tv)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
