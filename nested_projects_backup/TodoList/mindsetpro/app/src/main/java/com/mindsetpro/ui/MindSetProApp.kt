package com.mindsetpro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindsetpro.data.model.BedtimeItem
import com.mindsetpro.data.model.VoiceCommandResult
import com.mindsetpro.ui.analytics.AnalyticsScreen
import com.mindsetpro.ui.bedtime.BedtimeRoutineScreen
import com.mindsetpro.ui.dashboard.*
import com.mindsetpro.ui.onboarding.OnboardingScreen
import com.mindsetpro.ui.settings.SettingsScreen
import com.mindsetpro.ui.theme.MindSetColors
import com.mindsetpro.ui.voice.VoiceCommandScreen
import com.mindsetpro.utils.BedtimeRoutineManager
import com.mindsetpro.utils.PreferencesManager

/**
 * Root App Composable.
 *
 * Flow:
 *   1. Check if onboarding completed → show OnboardingScreen if not
 *   2. Main app with bottom nav (Dashboard / Analytics / Voice / Bedtime)
 *   3. Settings accessible from Dashboard header gear icon
 */
enum class AppTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Home", Icons.Filled.Home, Icons.Outlined.Home),
    ANALYTICS("Analytics", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    VOICE("Voice", Icons.Filled.Mic, Icons.Outlined.Mic),
    BEDTIME("Bedtime", Icons.Filled.Bedtime, Icons.Outlined.Bedtime)
}

enum class Screen { ONBOARDING, MAIN, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindSetProApp() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val viewModel: DashboardViewModel = viewModel()

    // ── Navigation State ─────────────────────────────────────────────────────
    var currentScreen by remember {
        mutableStateOf(if (prefs.onboardingCompleted) Screen.MAIN else Screen.ONBOARDING)
    }
    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }

    // ── Dialog State ─────────────────────────────────────────────────────────
    var showFabMenu by remember { mutableStateOf(false) }
    var showAddTask by remember { mutableStateOf(false) }
    var showAddHabit by remember { mutableStateOf(false) }

    // ── Voice State ──────────────────────────────────────────────────────────
    var isListening by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var lastVoiceResult by remember { mutableStateOf<VoiceCommandResult?>(null) }

    // ── Bedtime State ────────────────────────────────────────────────────────
    val bedtimeItems = remember {
        mutableStateListOf(
            *BedtimeRoutineManager.DEFAULT_ROUTINE.mapIndexed { i, name ->
                BedtimeItem(name = name, orderIndex = i)
            }.toTypedArray()
        )
    }
    var bedtimeCompletion by remember { mutableFloatStateOf(0f) }

    // ── Settings State ───────────────────────────────────────────────────────
    var habitReminderHour by remember { mutableIntStateOf(prefs.habitReminderHour) }
    var bedtimeHour by remember { mutableIntStateOf(prefs.bedtimeReminderHour) }
    var firebaseSyncEnabled by remember { mutableStateOf(prefs.firebaseSyncEnabled) }
    var llmFallbackEnabled by remember { mutableStateOf(prefs.llmFallbackEnabled) }

    // ── Analytics State ──────────────────────────────────────────────────────
    val snapshot by viewModel.snapshot.collectAsState()
    val streaks by viewModel.streakInfos.collectAsState()
    val clusters by viewModel.clusters.collectAsState()
    val dayProfile by viewModel.dayProfile.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val momentum by viewModel.momentum.collectAsState()

    // ═════════════════════════════════════════════════════════════════════════
    // Screen Router
    // ═════════════════════════════════════════════════════════════════════════

    when (currentScreen) {

        // ── Onboarding ───────────────────────────────────────────────────────
        Screen.ONBOARDING -> {
            OnboardingScreen(
                onComplete = {
                    prefs.onboardingCompleted = true
                    currentScreen = Screen.MAIN
                }
            )
        }

        // ── Settings ─────────────────────────────────────────────────────────
        Screen.SETTINGS -> {
            SettingsScreen(
                habitReminderHour = habitReminderHour,
                bedtimeHour = bedtimeHour,
                firebaseSyncEnabled = firebaseSyncEnabled,
                llmFallbackEnabled = llmFallbackEnabled,
                onHabitReminderChange = { habitReminderHour = it; prefs.habitReminderHour = it },
                onBedtimeChange = { bedtimeHour = it; prefs.bedtimeReminderHour = it },
                onFirebaseSyncToggle = { firebaseSyncEnabled = it; prefs.firebaseSyncEnabled = it },
                onLlmFallbackToggle = { llmFallbackEnabled = it; prefs.llmFallbackEnabled = it },
                onExportData = { /* Trigger DataExportManager */ },
                onImportData = { /* Launch file picker intent */ },
                onSeedDemoData = { /* Call SyntheticDataEngine.seedDatabase */ },
                onClearAllData = { /* Clear Room database */ },
                onBack = { currentScreen = Screen.MAIN }
            )
        }

        // ── Main App ─────────────────────────────────────────────────────────
        Screen.MAIN -> {
            Scaffold(
                containerColor = MindSetColors.background,
                topBar = {
                    if (currentTab == AppTab.DASHBOARD) {
                        TopAppBar(
                            title = {},
                            actions = {
                                IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                                    Icon(Icons.Default.Settings, "Settings",
                                        tint = MindSetColors.textMuted)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MindSetColors.background
                            )
                        )
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MindSetColors.surface,
                        contentColor = MindSetColors.text,
                        tonalElevation = 0.dp
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MindSetColors.accentCyan,
                                    selectedTextColor = MindSetColors.accentCyan,
                                    unselectedIconColor = MindSetColors.textMuted,
                                    unselectedTextColor = MindSetColors.textMuted,
                                    indicatorColor = MindSetColors.accentCyan.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (currentTab == AppTab.DASHBOARD) {
                        FloatingActionButton(
                            onClick = { showFabMenu = true },
                            containerColor = MindSetColors.accentCyan,
                            contentColor = MindSetColors.background
                        ) {
                            Icon(Icons.Default.Add, "Add")
                        }
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    when (currentTab) {
                        AppTab.DASHBOARD -> DashboardScreen(viewModel)

                        AppTab.ANALYTICS -> AnalyticsScreen(
                            snapshot = snapshot,
                            streakInfos = streaks,
                            clusters = clusters,
                            dayProfile = dayProfile,
                            categoryBreakdown = categoryBreakdown,
                            momentumScore = momentum.first,
                            momentumLabel = momentum.second
                        )

                        AppTab.VOICE -> VoiceCommandScreen(
                            isListening = isListening,
                            partialText = partialText,
                            lastResult = lastVoiceResult,
                            onStartListening = { isListening = true },
                            onStopListening = { isListening = false },
                            onExecuteCommand = { result ->
                                viewModel.executeVoiceCommand(result)
                                lastVoiceResult = null
                            }
                        )

                        AppTab.BEDTIME -> BedtimeRoutineScreen(
                            items = bedtimeItems,
                            completionPercent = bedtimeCompletion,
                            onToggleItem = { id, checked ->
                                val idx = bedtimeItems.indexOfFirst { it.id == id }
                                if (idx >= 0) {
                                    bedtimeItems[idx] = bedtimeItems[idx].copy(isChecked = checked)
                                    bedtimeCompletion =
                                        bedtimeItems.count { it.isChecked }.toFloat() /
                                            bedtimeItems.size.coerceAtLeast(1)
                                }
                            },
                            onReset = {
                                bedtimeItems.replaceAll { it.copy(isChecked = false) }
                                bedtimeCompletion = 0f
                            }
                        )
                    }
                }
            }

            // ── Dialogs ──────────────────────────────────────────────────────
            if (showFabMenu) {
                AddItemFabMenu(
                    onAddTask = { showAddTask = true },
                    onAddHabit = { showAddHabit = true },
                    onDismiss = { showFabMenu = false }
                )
            }
            if (showAddTask) {
                AddTaskDialog(
                    onDismiss = { showAddTask = false },
                    onConfirm = { name, cat, pri ->
                        viewModel.createTask(name, cat, pri)
                        showAddTask = false
                    }
                )
            }
            if (showAddHabit) {
                AddHabitDialog(
                    onDismiss = { showAddHabit = false },
                    onConfirm = { name, emoji, cat, days ->
                        viewModel.createHabit(name, emoji, cat, days)
                        showAddHabit = false
                    }
                )
            }
        }
    }
}
