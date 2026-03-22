package com.example.myapplication.ui.voice

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MindSetColors
import com.example.myapplication.utils.ElevenLabsManager
import com.example.myapplication.utils.SettingsManager
import com.example.myapplication.utils.VoiceIdentityManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAgentSettingsScreen(
    elevenLabsManager: ElevenLabsManager,
    voiceIdentityManager: VoiceIdentityManager,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    // State
    var voices by remember { mutableStateOf<List<ElevenLabsManager.VoiceInfo>>(emptyList()) }
    var selectedVoiceId by remember { mutableStateOf(settingsManager.selectedVoiceId) }
    var useElevenLabs by remember { mutableStateOf(settingsManager.useElevenLabsTts) }
    var wakeWordEnabled by remember { mutableStateOf(settingsManager.wakeWordEnabled) }
    var voiceStability by remember { mutableFloatStateOf(settingsManager.voiceStability) }
    var voiceSimilarity by remember { mutableFloatStateOf(settingsManager.voiceSimilarity) }
    var usageInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isPreviewPlaying by remember { mutableStateOf<String?>(null) }

    val isSpeaking by elevenLabsManager.isSpeaking.collectAsState()
    val isLoadingVoices by elevenLabsManager.isLoadingVoices.collectAsState()
    val enrollmentState by voiceIdentityManager.enrollmentState.collectAsState()
    val isRecording by voiceIdentityManager.isRecording.collectAsState()

    // Load voices on first composition
    LaunchedEffect(Unit) {
        voices = elevenLabsManager.getAvailableVoices()
        usageInfo = elevenLabsManager.getUsageInfo()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MindSetColors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // ── Header ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = MindSetColors.text)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Voice Agent Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MindSetColors.text
                )
            }
        }

        // ── Voice Engine Toggle ──
        item {
            SectionCard("Voice Engine") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ElevenLabs Premium Voice", color = MindSetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (useElevenLabs) "Using high-quality AI voices" else "Using device TTS (offline)",
                            color = MindSetColors.textMuted, fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = useElevenLabs,
                        onCheckedChange = {
                            useElevenLabs = it
                            settingsManager.useElevenLabsTts = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MindSetColors.accentCyan,
                            checkedThumbColor = Color.White
                        )
                    )
                }

                // Wake Word Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("\"Hey Vérité\" Wake Word", color = MindSetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Always listening in background", color = MindSetColors.textMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = wakeWordEnabled,
                        onCheckedChange = {
                            wakeWordEnabled = it
                            settingsManager.wakeWordEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MindSetColors.accentCyan,
                            checkedThumbColor = Color.White
                        )
                    )
                }
            }
        }

        // ── Voice Selection ──
        if (useElevenLabs) {
            item {
                SectionCard("Select Voice") {
                    if (isLoadingVoices) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MindSetColors.accentCyan, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            if (voices.isNotEmpty()) {
                items(voices.take(12)) { voice ->
                    VoiceCard(
                        voice = voice,
                        isSelected = voice.voiceId == selectedVoiceId,
                        isPlaying = isPreviewPlaying == voice.voiceId || (isSpeaking && isPreviewPlaying == voice.voiceId),
                        onSelect = {
                            selectedVoiceId = voice.voiceId
                            settingsManager.selectedVoiceId = voice.voiceId
                        },
                        onPreview = {
                            scope.launch {
                                isPreviewPlaying = voice.voiceId
                                if (voice.previewUrl != null) {
                                    elevenLabsManager.previewVoice(voice.previewUrl)
                                } else {
                                    elevenLabsManager.speak("Hello! I'm ${voice.name}, your Vérité voice assistant.", voice.voiceId)
                                }
                                isPreviewPlaying = null
                            }
                        }
                    )
                }
            }
        }

        // ── Voice Tuning ──
        if (useElevenLabs) {
            item {
                SectionCard("Voice Tuning") {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Stability", color = MindSetColors.text, fontSize = 13.sp)
                        Text("Higher = more consistent, Lower = more expressive", color = MindSetColors.textMuted, fontSize = 11.sp)
                        Slider(
                            value = voiceStability,
                            onValueChange = {
                                voiceStability = it
                                settingsManager.voiceStability = it
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MindSetColors.accentCyan,
                                activeTrackColor = MindSetColors.accentCyan
                            )
                        )

                        Spacer(Modifier.height(8.dp))

                        Text("Similarity Boost", color = MindSetColors.text, fontSize = 13.sp)
                        Text("Higher = closer to original voice", color = MindSetColors.textMuted, fontSize = 11.sp)
                        Slider(
                            value = voiceSimilarity,
                            onValueChange = {
                                voiceSimilarity = it
                                settingsManager.voiceSimilarity = it
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MindSetColors.accentCyan,
                                activeTrackColor = MindSetColors.accentCyan
                            )
                        )
                    }
                }
            }
        }

        // ── Voice ID Enrollment ──
        item {
            SectionCard("Voice ID") {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            null,
                            tint = when (enrollmentState) {
                                is VoiceIdentityManager.EnrollmentState.Enrolled -> MindSetColors.accentGreen
                                is VoiceIdentityManager.EnrollmentState.InProgress -> MindSetColors.accentOrange
                                else -> MindSetColors.textMuted
                            },
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                when (enrollmentState) {
                                    is VoiceIdentityManager.EnrollmentState.Enrolled -> "Voice ID Active"
                                    is VoiceIdentityManager.EnrollmentState.InProgress -> "Enrollment In Progress"
                                    is VoiceIdentityManager.EnrollmentState.Processing -> "Processing Voice Print..."
                                    is VoiceIdentityManager.EnrollmentState.Error -> "Enrollment Error"
                                    else -> "Set Up Voice ID"
                                },
                                color = MindSetColors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                when (enrollmentState) {
                                    is VoiceIdentityManager.EnrollmentState.Enrolled ->
                                        "Vérité recognizes your voice (${voiceIdentityManager.enrolledUserName ?: "User"})"
                                    is VoiceIdentityManager.EnrollmentState.InProgress ->
                                        "Step ${(enrollmentState as VoiceIdentityManager.EnrollmentState.InProgress).step + 1} of 3"
                                    is VoiceIdentityManager.EnrollmentState.Error ->
                                        (enrollmentState as VoiceIdentityManager.EnrollmentState.Error).message
                                    else -> "Teach Vérité to recognize your voice"
                                },
                                color = MindSetColors.textMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    when (val state = enrollmentState) {
                        is VoiceIdentityManager.EnrollmentState.NotEnrolled -> {
                            Button(
                                onClick = {
                                    voiceIdentityManager.startEnrollment(
                                        voiceIdentityManager.enrolledUserName ?: "User"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MindSetColors.accentCyan),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Mic, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start Voice Enrollment")
                            }
                        }

                        is VoiceIdentityManager.EnrollmentState.InProgress -> {
                            Surface(
                                color = MindSetColors.surface3,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Please say:",
                                        color = MindSetColors.textMuted,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "\"${state.prompt}\"",
                                        color = MindSetColors.accentCyan,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                voiceIdentityManager.recordEnrollmentSample(state.step)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isRecording) MindSetColors.accentRed else MindSetColors.accentCyan
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                            null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isRecording) "Recording..." else "Tap to Record")
                                    }

                                    // Progress dots
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(3) { index ->
                                            Box(
                                                modifier = Modifier
                                                    .size(if (index == state.step) 12.dp else 8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            index < state.step -> MindSetColors.accentGreen
                                                            index == state.step -> MindSetColors.accentCyan
                                                            else -> MindSetColors.surface3
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is VoiceIdentityManager.EnrollmentState.Processing -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MindSetColors.accentCyan, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Analyzing your voice pattern...", color = MindSetColors.textMuted, fontSize = 13.sp)
                                }
                            }
                        }

                        is VoiceIdentityManager.EnrollmentState.Enrolled -> {
                            OutlinedButton(
                                onClick = { voiceIdentityManager.resetEnrollment() },
                                border = BorderStroke(1.dp, MindSetColors.accentRed.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Reset Voice ID", color = MindSetColors.accentRed, fontSize = 13.sp)
                            }
                        }

                        is VoiceIdentityManager.EnrollmentState.Error -> {
                            Button(
                                onClick = {
                                    voiceIdentityManager.startEnrollment(
                                        voiceIdentityManager.enrolledUserName ?: "User"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MindSetColors.accentOrange),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retry Enrollment")
                            }
                        }
                    }
                }
            }
        }

        // ── API Usage ──
        if (useElevenLabs && usageInfo != null) {
            item {
                SectionCard("API Usage") {
                    val (used, limit) = usageInfo!!
                    val pct = if (limit > 0) used.toFloat() / limit else 0f
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Characters Used", color = MindSetColors.textMuted, fontSize = 12.sp)
                            Text("$used / $limit", color = MindSetColors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = when {
                                pct > 0.9f -> MindSetColors.accentRed
                                pct > 0.7f -> MindSetColors.accentOrange
                                else -> MindSetColors.accentGreen
                            },
                            trackColor = MindSetColors.surface3
                        )
                    }
                }
            }
        }
    }
}

// ── Reusable Components ──────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MindSetColors.surface2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                title,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                color = MindSetColors.accentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun VoiceCard(
    voice: ElevenLabsManager.VoiceInfo,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MindSetColors.accentCyan.copy(alpha = 0.15f) else MindSetColors.surface2,
        border = if (isSelected) BorderStroke(1.5.dp, MindSetColors.accentCyan) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MindSetColors.accentCyan.copy(alpha = 0.3f)
                        else MindSetColors.surface3
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSelected) Icons.Default.Check else Icons.Default.Person,
                    null,
                    tint = if (isSelected) MindSetColors.accentCyan else MindSetColors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(voice.name, color = MindSetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    voice.labels["gender"]?.let { gender ->
                        Text(gender.replaceFirstChar { it.uppercase() }, color = MindSetColors.textMuted, fontSize = 11.sp)
                    }
                    voice.labels["accent"]?.let { accent ->
                        Text("• $accent", color = MindSetColors.textMuted, fontSize = 11.sp)
                    }
                    voice.labels["age"]?.let { age ->
                        Text("• $age", color = MindSetColors.textMuted, fontSize = 11.sp)
                    }
                }
            }

            // Preview button
            IconButton(
                onClick = onPreview,
                modifier = Modifier
                    .size(36.dp)
                    .background(MindSetColors.surface3, CircleShape)
            ) {
                if (isPlaying) {
                    CircularProgressIndicator(
                        color = MindSetColors.accentCyan,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        "Preview",
                        tint = MindSetColors.accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
