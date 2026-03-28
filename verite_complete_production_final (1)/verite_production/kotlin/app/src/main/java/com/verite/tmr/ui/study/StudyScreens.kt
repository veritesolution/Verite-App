package com.verite.tmr.ui.study

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.verite.tmr.data.models.*

// ─────────────────────────────────────────────────────────────────────────────
// Study Home — entry point for study tools
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyHomeScreen(nav: NavController, vm: StudyViewModel = hiltViewModel()) {
    val error by vm.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Tools", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Prepare for sleep", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Flashcard study
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("flashcards") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Style, contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column {
                            Text("Flashcards", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Study concepts with real performance tracking",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // Pre-sleep quiz
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("quiz/pre_sleep") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Quiz, contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Column {
                            Text("Pre-Sleep Quiz", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("Baseline MCQ before TMR session",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // Post-sleep quiz
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("quiz/post_sleep") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.WbSunny, contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Column {
                            Text("Post-Sleep Quiz", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Measure consolidation after TMR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // Audio study
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("audio") },
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Headphones, contentDescription = null,
                            modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Audio Study", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Listen to concepts and preview TMR cues",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Flashcard Screen — animated card flip with rating
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(nav: NavController, vm: StudyViewModel = hiltViewModel()) {
    val state by vm.studyState.collectAsStateWithLifecycle()
    val complete by vm.studyComplete.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.startStudy() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashcards") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.nTotal > 0) {
                        Text("${state.nStudied}/${state.nTotal}",
                            modifier = Modifier.padding(end = 16.dp),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                complete is UiState.Success -> {
                    val data = (complete as UiState.Success<StudyCompleteResponse>).data
                    StudyCompleteCard(data, onDone = { nav.popBackStack() })
                }
                state.currentCard != null -> {
                    FlashCardView(
                        card = state.currentCard!!,
                        isFlipped = state.isFlipped,
                        lastAnswer = state.lastAnswer,
                        onFlip = { vm.flipCard() },
                        onRate = { rating -> vm.submitAnswer(rating) },
                    )
                }
                else -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun FlashCardView(
    card: FlashCard, isFlipped: Boolean, lastAnswer: CardAnswerResponse?,
    onFlip: () -> Unit, onRate: (Int) -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400), label = "cardFlip"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Animated card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clickable { if (!isFlipped) onFlip() },
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (rotation <= 90f) {
                    // Front — concept name
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(card.category.uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(card.concept, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Text("Tap to reveal", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // Back — definition (mirrored to read correctly)
                    Column(
                        modifier = Modifier.graphicsLayer { rotationY = 180f },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(card.concept, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(card.definition, style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Rating buttons (only show after flip)
        if (isFlipped) {
            Text("How well did you know this?", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val labels = listOf("1" to "Forgot", "2" to "Hard", "3" to "OK", "4" to "Good", "5" to "Easy")
                labels.forEach { (num, label) ->
                    val rating = num.toInt()
                    val color = when (rating) {
                        1 -> MaterialTheme.colorScheme.error
                        2 -> Color(0xFFE65100)
                        3 -> Color(0xFFF57F17)
                        4 -> Color(0xFF2E7D32)
                        5 -> Color(0xFF1565C0)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    FilledTonalButton(
                        onClick = { onRate(rating) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(alpha = 0.15f))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(num, fontWeight = FontWeight.Bold, color = color)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                        }
                    }
                }
            }

            // Show strength feedback
            lastAnswer?.let { ans ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = if (ans.sweetSpot) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "Strength: ${"%.0f".format(ans.strength * 100)}% — ${ans.tier.replace("_", " ")}",
                        modifier = Modifier.padding(8.dp, 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ans.sweetSpot) Color(0xFF1B5E20) else Color(0xFFE65100),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyCompleteCard(data: StudyCompleteResponse, onDone: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null,
                modifier = Modifier.size(48.dp), tint = Color(0xFF2E7D32))
            Text("Study Complete!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("${data.nStudied} concepts studied", style = MaterialTheme.typography.bodyMedium)
            Text("${data.nSweetSpot} in sweet spot — ${data.nCuesQueued} TMR cues queued",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(data.message, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, color = Color(0xFF2E7D32))
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quiz Screen — MCQ with colour-coded feedback
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(nav: NavController, mode: String, vm: StudyViewModel = hiltViewModel()) {
    val state by vm.quizState.collectAsStateWithLifecycle()
    val complete by vm.quizComplete.collectAsStateWithLifecycle()

    LaunchedEffect(mode) { vm.startQuiz(mode) }

    val title = if (mode == "post_sleep") "Post-Sleep Quiz" else "Pre-Sleep Quiz"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.nTotal > 0) {
                        Text("${state.nAnswered}/${state.nTotal} · ${state.nCorrect} correct",
                            modifier = Modifier.padding(end = 16.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                complete is UiState.Success -> {
                    val data = (complete as UiState.Success<QuizCompleteResponse>).data
                    QuizCompleteCard(data, onDone = { nav.popBackStack() })
                }
                state.currentQuestion != null -> {
                    QuizQuestionView(
                        question = state.currentQuestion!!,
                        lastAnswer = state.lastAnswer,
                        showFeedback = state.showingFeedback,
                        onAnswer = { idx -> vm.answerQuestion(idx) },
                    )
                }
                else -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun QuizQuestionView(
    question: QuizQuestion, lastAnswer: QuizAnswerResponse?,
    showFeedback: Boolean, onAnswer: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Progress
        LinearProgressIndicator(
            progress = { (question.index.toFloat()) / question.nTotal.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(question.question, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        question.options.forEachIndexed { idx, option ->
            val bgColor = if (showFeedback && lastAnswer != null) {
                when {
                    idx == lastAnswer.correctIndex -> Color(0xFFE8F5E9)
                    idx == lastAnswer.selectedIndex && !lastAnswer.correct -> Color(0xFFFFEBEE)
                    else -> MaterialTheme.colorScheme.surface
                }
            } else MaterialTheme.colorScheme.surface

            val borderColor = if (showFeedback && lastAnswer != null) {
                when {
                    idx == lastAnswer.correctIndex -> Color(0xFF2E7D32)
                    idx == lastAnswer.selectedIndex && !lastAnswer.correct -> Color(0xFFC62828)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            } else MaterialTheme.colorScheme.outlineVariant

            val animBg by animateColorAsState(targetValue = bgColor, label = "optBg")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = !showFeedback) { onAnswer(idx) },
                colors = CardDefaults.outlinedCardColors(containerColor = animBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = null),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${'A' + idx}", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        color = borderColor)
                    Text(option, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    if (showFeedback && lastAnswer != null && idx == lastAnswer.correctIndex) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizCompleteCard(data: QuizCompleteResponse, onDone: () -> Unit) {
    val emoji = when {
        data.accuracyPct >= 80 -> "🎯"
        data.accuracyPct >= 60 -> "👍"
        else -> "📚"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(emoji, fontSize = 40.sp)
            Text("Quiz Complete!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("${data.nCorrect}/${data.nTotal} correct (${"%.0f".format(data.accuracyPct)}%)",
                style = MaterialTheme.typography.titleMedium)
            Text("Avg response: ${"%.1f".format(data.avgRtS)}s",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (data.mode == "post_sleep" && data.weightUpdates > 0) {
                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp)) {
                    Text("${data.weightUpdates} weight updates applied to strength formula",
                        modifier = Modifier.padding(12.dp, 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audio Screen — listen to concepts + preview TMR cues
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(nav: NavController, vm: StudyViewModel = hiltViewModel()) {
    val concepts by vm.audioConcepts.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val currentConcept by vm.currentPlayingConcept.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadAudioConcepts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Study") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (playing) {
                        IconButton(onClick = { vm.stopAudio() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (concepts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No concepts loaded. Upload a document first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(concepts) { concept ->
                    val isActive = currentConcept == concept.key && playing
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (isActive) CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else CardDefaults.cardColors()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(concept.key, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall)
                            Text(concept.definition, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { vm.playStudyAudio(concept.key) },
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null,
                                        modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Study", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(onClick = { vm.playCuePreview(concept.key) },
                                    modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.NightsStay, contentDescription = null,
                                        modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cue", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
