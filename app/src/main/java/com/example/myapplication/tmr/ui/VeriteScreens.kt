package com.example.myapplication.tmr.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.tmr.data.models.*
import com.example.myapplication.tmr.data.network.VeriteWebSocket.ConnectionState
import com.example.myapplication.tmr.ui.study.StudyHomeScreen
import com.example.myapplication.tmr.ui.study.FlashcardScreen
import com.example.myapplication.tmr.ui.study.QuizScreen
import com.example.myapplication.tmr.ui.study.AudioScreen
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// Navigation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VeriteNavGraph(vm: SessionViewModel = viewModel(factory = SessionViewModel.provideFactory(LocalContext.current))) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home")    { HomeScreen(nav, vm) }
        composable("session") { SessionScreen(nav, vm) }
        composable("report")  { ReportScreen(nav, vm) }
        // Study system routes
        composable("study")      { StudyHomeScreen(nav) }
        composable("flashcards") { FlashcardScreen(nav) }
        composable("quiz/pre_sleep")  { QuizScreen(nav, mode = "pre_sleep") }
        composable("quiz/post_sleep") { QuizScreen(nav, mode = "post_sleep") }
        composable("audio")      { AudioScreen(nav) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Home screen — start / stop session, upload document
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController, vm: SessionViewModel) {
    val context = LocalContext.current
    val sessionState by vm.sessionUiState.collectAsStateWithLifecycle()
    val startResponse by vm.startResponse.collectAsStateWithLifecycle()
    val uploadState by vm.uploadState.collectAsStateWithLifecycle()
    val error by vm.errorMessage.collectAsStateWithLifecycle()

    // File picker — accepts PDF, DOCX, PPTX, TXT
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.uploadDocument(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vérité TMR", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = {
            error?.let { msg ->
                Snackbar(
                    action = { TextButton(onClick = { vm.clearError() }) { Text("Dismiss") } }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Connection status ────────────────────────────────────────────
            item { ServerStatusCard(vm) }

            // ── Session card ─────────────────────────────────────────────────
            item {
                SessionControlCard(
                    sessionState  = sessionState,
                    startResponse = startResponse,
                    onStart       = { mode -> vm.startSession(mode = mode) },
                    onStop        = { vm.stopSession() },
                    onViewLive    = { nav.navigate("session") },
                    onViewReport  = { nav.navigate("report") }
                )
            }

            // ── Document upload card ─────────────────────────────────────────
            item {
                DocumentUploadCard(
                    uploadState = uploadState,
                    sessionActive = sessionState.active,
                    onPickFile = {
                        filePicker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "text/plain", "text/markdown"
                            )
                        )
                    }
                )
            }

            // ── Study tools ──────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Study Tools", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("Flashcards, quizzes, and audio study — feeds real performance data into TMR targeting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                        Button(
                            onClick = { nav.navigate("study") },
                            enabled = sessionState.active,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (sessionState.active) "Open Study Tools" else "Start session first")
                        }
                    }
                }
            }

            // ── Safety notes ─────────────────────────────────────────────────
            item { SafetyNotesCard() }
        }
    }
}

@Composable
private fun ServerStatusCard(vm: SessionViewModel) {
    val connection by vm.connectionState.collectAsStateWithLifecycle()

    val (icon, label, color) = when (connection) {
        ConnectionState.Connected    -> Triple(Icons.Default.Wifi,        "Stream connected",   Color(0xFF2E7D32))
        ConnectionState.Connecting,
        ConnectionState.Reconnecting -> Triple(Icons.Default.Sync,        "Connecting…",        Color(0xFFF57F17))
        ConnectionState.Error        -> Triple(Icons.Default.WifiOff,     "Connection error",   Color(0xFFC62828))
        ConnectionState.Disconnected -> Triple(Icons.Default.WifiOff,     "Not connected",      MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Column {
                Text("WebSocket stream", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, color = color, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SessionControlCard(
    sessionState: SessionUiState,
    startResponse: UiState<StartSessionResponse>,
    onStart: (String) -> Unit,
    onStop: () -> Unit,
    onViewLive: () -> Unit,
    onViewReport: () -> Unit
) {
    var selectedMode by remember { mutableStateOf("simulation") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (!sessionState.active) {
                // Mode selector
                Text("Mode", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("simulation" to "Simulation", "live" to "Live EEG").forEach { (value, label) ->
                        FilterChip(
                            selected = selectedMode == value,
                            onClick  = { selectedMode = value },
                            label    = { Text(label) }
                        )
                    }
                }

                Button(
                    onClick  = { onStart(selectedMode) },
                    enabled  = startResponse !is UiState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (startResponse is UiState.Loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Start Session")
                }
            } else {
                // Active session info
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Running", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                        Text(sessionState.sessionId?.take(8) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatElapsed(sessionState.elapsedS),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace)
                        Text("${sessionState.nCues} cues",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onViewLive, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Equalizer, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Live View")
                    }
                    Button(onClick = onViewReport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Assessment, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Report")
                    }
                }

                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop Session")
                }
            }
        }
    }
}

@Composable
private fun DocumentUploadCard(
    uploadState: UiState<DocumentResponse>,
    sessionActive: Boolean,
    onPickFile: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Study Document", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Upload the notes you want to reactivate during sleep.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedButton(
                onClick  = onPickFile,
                enabled  = sessionActive && uploadState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (!sessionActive) "Start session first" else "Pick PDF / DOCX / TXT")
            }

            when (val s = uploadState) {
                is UiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is UiState.Success -> {
                    val doc = s.data
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("✓ ${doc.filename}", fontWeight = FontWeight.Medium, color = Color(0xFF1B5E20))
                            Text("${doc.nConcepts} concepts extracted · ${doc.nCuesQueued} cues queued · ${doc.audioBackend} TTS",
                                style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C))
                        }
                    }
                }
                is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                else -> {}
            }
        }
    }
}

@Composable
private fun SafetyNotesCard() {
    val notes = listOf(
        "Simulation mode: runs 8h compressed. No EEG hardware needed.",
        "Live mode: requires EEG hardware WebSocket connection.",
        "Volume is auto-limited to 35% max to protect hearing during sleep.",
        "SpindleCNN in band-power proxy mode until trained on DREAMS dataset."
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Notes", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            notes.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("·", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(note, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Live Session Screen — real-time brain state dashboard
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(nav: NavController, vm: SessionViewModel) {
    val tick by vm.latestTick.collectAsStateWithLifecycle()
    val history by vm.tickHistory.collectAsStateWithLifecycle()
    val sessionState by vm.sessionUiState.collectAsStateWithLifecycle()

    // Flash card when cue fires
    var lastCue by remember { mutableStateOf<CueInfo?>(null) }
    LaunchedEffect(Unit) {
        vm.cueEvents.collect { lastCue = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Session") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.fetchReport() }) {
                        Icon(Icons.Default.Assessment, contentDescription = "Report")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Sleep stage + phase dial ──────────────────────────────────────
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SleepStageCard(tick, modifier = Modifier.weight(1f))
                    PhaseDialCard(tick, modifier = Modifier.weight(1f))
                }
            }

            // ── Detection metrics bar ─────────────────────────────────────────
            item { DetectionMetricsRow(tick) }

            // ── Sparkline chart (spindle prob over time) ──────────────────────
            item { SpindleSparklineCard(history) }

            // ── Last cue fired ────────────────────────────────────────────────
            lastCue?.let { cue ->
                item { LastCueCard(cue) }
            }

            // ── Cue log ───────────────────────────────────────────────────────
            item {
                Text("Session Stats",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("Cues", sessionState.nCues.toString(), Modifier.weight(1f))
                    StatChip("Mode", sessionState.mode ?: "—", Modifier.weight(1f))
                    StatChip("Elapsed", formatElapsed(sessionState.elapsedS), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SleepStageCard(tick: TickEvent?, modifier: Modifier = Modifier) {
    val stage = tick?.stage ?: "—"
    val stageColor = when (stage) {
        "N3" -> Color(0xFF1565C0)
        "N2" -> Color(0xFF1976D2)
        "N1" -> Color(0xFF42A5F5)
        "REM" -> Color(0xFF7B1FA2)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Stage", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stage, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = stageColor)
            Text(if (tick?.deliveryWindow == true) "Delivery window" else "Monitoring",
                style = MaterialTheme.typography.bodySmall,
                color = if (tick?.deliveryWindow == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhaseDialCard(tick: TickEvent?, modifier: Modifier = Modifier) {
    val phaseDeg = tick?.phaseDeg ?: 0.0
    val needleColor = tick?.phaseColor?.let { c ->
        when (c) {
            PhaseColor.GREEN -> Color(0xFF2E7D32)
            PhaseColor.AMBER -> Color(0xFFF57F17)
            PhaseColor.RED   -> Color(0xFFC62828)
        }
    } ?: Color.Gray

    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SO Phase", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PhaseDial(phaseDeg = phaseDeg, color = needleColor, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(4.dp))
            Text("${phaseDeg.toInt()}°", style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PhaseDial(phaseDeg: Double, color: Color, modifier: Modifier = Modifier) {
    val sweepColor = Color(0xFF90CAF9)
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = minOf(cx, cy) - 6.dp.toPx()

        // Background arc
        drawArc(Color.LightGray.copy(alpha = 0.3f), startAngle = 0f, sweepAngle = 360f,
            useCenter = false, topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx()))

        // SO up-state window: 135–225 deg (Mölle 2002 × π)
        drawArc(sweepColor.copy(alpha = 0.4f), startAngle = 135f - 90f, sweepAngle = 90f,
            useCenter = false, topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx()))

        // Needle
        val angleRad = Math.toRadians(phaseDeg - 90.0)
        val nx = (cx + r * cos(angleRad)).toFloat()
        val ny = (cy + r * sin(angleRad)).toFloat()
        drawLine(color, start = Offset(cx, cy), end = Offset(nx, ny),
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(cx, cy))
    }
}

@Composable
private fun DetectionMetricsRow(tick: TickEvent?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricGauge("Spindle",  tick?.spindleProb ?: 0.0, Color(0xFF1565C0), Modifier.weight(1f))
        MetricGauge("Arousal",  tick?.arousalRisk  ?: 0.0, Color(0xFFB71C1C), Modifier.weight(1f))
        MetricGauge("Coupling", tick?.couplingMi   ?: 0.0, Color(0xFF1B5E20), Modifier.weight(1f))
    }
}

@Composable
private fun MetricGauge(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    val animValue by animateFloatAsState(targetValue = value.toFloat(), label = label,
        animationSpec = tween(durationMillis = 200))
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animValue },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(2.dp))
            Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SpindleSparklineCard(history: List<TickEvent>) {
    if (history.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Spindle probability", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                drawSparkline(history.map { it.spindleProb.toFloat() }, Color(0xFF1565C0))
            }
        }
    }
}

private fun DrawScope.drawSparkline(values: List<Float>, color: Color) {
    if (values.size < 2) return
    val w = size.width
    val h = size.height
    val stepX = w / (values.size - 1)
    val baseline = 0.15f  // spindle threshold line

    // Threshold line
    val threshY = h - baseline * h
    drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, threshY), Offset(w, threshY), strokeWidth = 1.dp.toPx())

    // Sparkline
    for (i in 0 until values.size - 1) {
        val x1 = i * stepX
        val x2 = (i + 1) * stepX
        val y1 = h - values[i].coerceIn(0f, 1f) * h
        val y2 = h - values[i + 1].coerceIn(0f, 1f) * h
        val lineColor = if (values[i] >= baseline) color else Color.Gray.copy(alpha = 0.5f)
        drawLine(lineColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun LastCueCard(cue: CueInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2E7D32)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column {
                Text("Cue delivered", style = MaterialTheme.typography.labelMedium, color = Color(0xFF1B5E20))
                Text(cue.concept, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                Text("${cue.cueType} · ${cue.phaseDeg.toInt()}° · spindle ${cue.spindleProb.let { "%.2f".format(it) }}",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C))
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Report Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(nav: NavController, vm: SessionViewModel) {
    val reportState by vm.report.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.fetchReport() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Report") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.fetchReport() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = reportState) {
            is UiState.Idle    -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No report yet") }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error   -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> ReportContent(s.data, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ReportContent(report: SessionReport, modifier: Modifier = Modifier) {
    val stats = report.stats
    val gate  = report.gateSummary

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Session header ───────────────────────────────────────────────────
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Session ${report.sessionId.take(8)}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Mode: ${report.mode}  ·  Duration: ${formatElapsed(report.elapsedS)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Spindle: ${report.spindleMode}  ·  Arousal: ${report.arousalMode}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }

        // ── Delivery stats ───────────────────────────────────────────────────
        item {
            Text("Delivery Statistics", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val statItems = listOf(
                    "Total cues"     to stats.totalCuesDelivered.toString(),
                    "Spindle-coupled" to "${stats.spindleCoupledPct}%",
                    "SO up-state"    to "${stats.soUpstatePct}%",
                    "Unique concepts" to stats.uniqueConceptsCued.toString(),
                    "Policy"         to stats.policyMode,
                    "Delivery rate"  to "${gate.deliveryRatePct}%"
                )
                items(statItems) { (label, value) ->
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // ── Gate rejection breakdown ─────────────────────────────────────────
        if (gate.blockReasons.isNotEmpty()) {
            item {
                Text("Gate Rejections (${gate.blocked} total)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        gate.blockReasons.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                            val total = gate.blocked.coerceAtLeast(1)
                            val frac  = count.toFloat() / total
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(reason, style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace)
                                    Text("$count", style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold)
                                }
                                LinearProgressIndicator(
                                    progress = { frac },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Cue log ──────────────────────────────────────────────────────────
        if (report.cueLog.isNotEmpty()) {
            item {
                Text("Recent Cues (last ${report.cueLog.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(report.cueLog.reversed()) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.concept, fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("${entry.cueType}  ·  ${entry.phaseDeg.toInt()}°  ·  " +
                                    "spindle ${"%.2f".format(entry.spindleProb)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatElapsed(s: Double): String {
    val totalSeconds = s.toInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
           else       "%02d:%02d".format(m, sec)
}
