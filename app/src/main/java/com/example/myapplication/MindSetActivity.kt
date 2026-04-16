package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.dashboard.DashboardScreen
import com.example.myapplication.ui.dashboard.DashboardViewModel
import com.example.myapplication.ui.analytics.AnalyticsScreen
import com.example.myapplication.ui.bedtime.BedtimeRoutineScreen
import com.example.myapplication.ui.theme.MindSetProTheme
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.utils.VoiceInputHandler
import com.example.myapplication.utils.VoiceOutputHandler
import com.example.myapplication.utils.VoiceCommandProcessor
import com.example.myapplication.utils.FullVoiceCommandProcessor
import com.example.myapplication.utils.FullVoiceCommandProcessor.FullIntent
import com.example.myapplication.ml.LlmVoiceProcessor
import com.example.myapplication.ui.tasks.TaskDetailScreen
import com.example.myapplication.ui.todo.TodoMainScreen
import com.example.myapplication.viewmodel.TodoViewModel
import com.example.myapplication.ui.settings.SettingsViewModel
import com.example.myapplication.data.model.VoiceCommandResult
import com.example.myapplication.data.model.Intent
import com.example.myapplication.data.model.Task
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.TextMuted
import androidx.activity.viewModels

import com.example.myapplication.tmr.data.network.VeriteClient
import com.example.myapplication.tmr.di.TmrDependencyContainer
import com.example.myapplication.tmr.ui.VeriteNavGraph
import com.example.myapplication.ui.notification.NotificationViewModel
import com.example.myapplication.ui.notification.NotificationPanel
import com.example.myapplication.ui.notification.VeriteToastBanner
import com.example.myapplication.data.repository.NotificationRepository
import com.example.myapplication.data.model.AppNotification
import com.example.myapplication.utils.ElevenLabsManager
import com.example.myapplication.utils.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MindSetActivity : ComponentActivity() {

    private lateinit var voiceInputHandler: VoiceInputHandler
    private lateinit var voiceOutputHandler: VoiceOutputHandler
    private lateinit var elevenLabsManager: ElevenLabsManager
    private lateinit var settingsManager: SettingsManager

    private val settingsViewModel: SettingsViewModel by viewModels()

    // Pending voice command from wake word (e.g. "start my sleep session")
    private val _pendingVoiceCommand = MutableStateFlow<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (audioGranted) {
            settingsViewModel.toggleWakeWord(true)
            toggleWakeWordService(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        VeriteClient.configure(
            baseUrl = BuildConfig.VERITE_SERVER_URL,
            apiKey = BuildConfig.VERITE_API_KEY,
            debug = BuildConfig.DEBUG
        )
        TmrDependencyContainer.initialize(this)

        settingsManager = SettingsManager(this)
        elevenLabsManager = ElevenLabsManager(this)

        try {
            voiceInputHandler = VoiceInputHandler(this)
            voiceOutputHandler = VoiceOutputHandler(this)
            voiceInputHandler.initialize()
            voiceOutputHandler.initialize()
        } catch (e: Exception) {
            Log.e("MindSetActivity", "Error initializing voice handlers", e)
        }

        handleVoiceIntent(intent)

        setContent {
            MindSetProTheme {
                com.example.myapplication.ui.home.SkyBackground {
                    val navController = rememberNavController()
                    val viewModel: DashboardViewModel = viewModel()
                    val todoViewModel: TodoViewModel = viewModel()
                    val notifViewModel: NotificationViewModel = viewModel()

                    // Notification state
                    val notifications by notifViewModel.notifications.collectAsState()
                    val unreadCount by notifViewModel.unreadCount.collectAsState()
                    val isPanelOpen by notifViewModel.isPanelOpen.collectAsState()

                    // Toast banner state (shows latest notification briefly)
                    var toastNotification by remember { mutableStateOf<AppNotification?>(null) }
                    LaunchedEffect(notifications) {
                        val latest = notifications.firstOrNull()
                        if (latest != null && !latest.isRead) {
                            toastNotification = latest
                            delay(4000)
                            toastNotification = null
                        }
                    }

                    val voiceState by voiceInputHandler.state.collectAsState()
                    var partialText by remember { mutableStateOf("") }

                    // Process pending voice command passed from wake word service
                    val pendingCommand by _pendingVoiceCommand.collectAsState()
                    LaunchedEffect(pendingCommand) {
                        val cmd = pendingCommand ?: return@LaunchedEffect
                        _pendingVoiceCommand.value = null
                        Log.i("MindSetActivity", "Processing wake word command: $cmd")
                        partialText = cmd
                        try {
                            val fullResult = FullVoiceCommandProcessor.process(cmd)
                            if (fullResult.intent != FullIntent.UNKNOWN) {
                                processVoiceIntent(fullResult, navController)
                            } else {
                                smartSpeak("Sorry, I didn't understand that. Try again.")
                            }
                        } catch (e: Exception) {
                            Log.e("MindSetActivity", "Error processing wake command", e)
                            smartSpeak("I had trouble with that command")
                        }
                    }

                    val wakeWordEnabled by settingsViewModel.wakeWordEnabled.collectAsState()
                    val habitReminderHour by settingsViewModel.habitReminderHour.collectAsState()
                    val bedtimeHour by settingsViewModel.bedtimeHour.collectAsState()
                    val firebaseSyncEnabled by settingsViewModel.firebaseSyncEnabled.collectAsState()
                    val llmFallbackEnabled by settingsViewModel.llmFallbackEnabled.collectAsState()

                    // Ensure service state matches saved preference
                    LaunchedEffect(wakeWordEnabled) {
                        toggleWakeWordService(wakeWordEnabled)
                    }

                    LaunchedEffect(voiceState) {
                        when (val state = voiceState) {
                            is VoiceInputHandler.VoiceState.PartialResult -> partialText = state.text
                            is VoiceInputHandler.VoiceState.FinalResult -> {
                                partialText = state.text

                                // ── Primary: FullVoiceCommandProcessor handles ALL app features ──
                                try {
                                    val fullResult = FullVoiceCommandProcessor.process(state.text)

                                if (fullResult.intent != FullIntent.UNKNOWN) {
                                    when (fullResult.intent) {

                                        // ── TASKS ──
                                        FullIntent.ADD_TASK -> {
                                            val name = fullResult.entityName ?: state.text
                                            viewModel.createTask(name, fullResult.category, fullResult.priority)
                                            smartSpeak("Task added: $name")
                                        }
                                        FullIntent.COMPLETE_TASK -> {
                                            viewModel.executeVoiceCommand(
                                                VoiceCommandResult(Intent.COMPLETE_TASK, 0.9f, fullResult.entityName)
                                            )
                                            smartSpeak("Task marked complete")
                                        }
                                        FullIntent.DELETE_TASK -> {
                                            viewModel.executeVoiceCommand(
                                                VoiceCommandResult(Intent.COMPLETE_TASK, 0.9f, fullResult.entityName)
                                            )
                                            smartSpeak("Task removed")
                                        }
                                        FullIntent.LIST_TASKS -> {
                                            navController.navigate("todo_main")
                                            smartSpeak("Here are your tasks")
                                        }

                                        // ── HABITS ──
                                        FullIntent.ADD_HABIT -> {
                                            viewModel.executeVoiceCommand(
                                                VoiceCommandResult(Intent.ADD_HABIT, 0.9f, fullResult.entityName, category = fullResult.category)
                                            )
                                            smartSpeak("Habit added: ${fullResult.entityName}")
                                        }
                                        FullIntent.TOGGLE_HABIT -> {
                                            viewModel.executeVoiceCommand(
                                                VoiceCommandResult(Intent.TOGGLE_HABIT, 0.9f, fullResult.entityName)
                                            )
                                            smartSpeak("Habit updated")
                                        }
                                        FullIntent.QUERY_STREAK -> {
                                            navController.navigate("analytics")
                                            smartSpeak("Opening your streak insights")
                                        }
                                        FullIntent.LIST_HABITS -> {
                                            navController.navigate("dashboard")
                                            smartSpeak("Here are your habits")
                                        }

                                        FullIntent.UPDATE_TASK_PRIORITY -> {
                                            smartSpeak("Task priority updated")
                                        }

                                        // ── HABITS (extended) ──
                                        FullIntent.DELETE_HABIT -> {
                                            viewModel.executeVoiceCommand(
                                                VoiceCommandResult(Intent.COMPLETE_TASK, 0.9f, fullResult.entityName)
                                            )
                                            smartSpeak("Habit removed")
                                        }

                                        // ── SLEEP & SOUND ──
                                        FullIntent.START_SLEEP_SESSION -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Starting sleep session")
                                        }
                                        FullIntent.STOP_SLEEP_SESSION -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Stopping sleep tracking")
                                        }
                                        FullIntent.PLAY_SLEEP_SOUND -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Playing sleep sounds")
                                        }
                                        FullIntent.PLAY_FOCUS_SOUND -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Playing focus music")
                                        }
                                        FullIntent.PLAY_RELAX_SOUND -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Playing relaxation music")
                                        }
                                        FullIntent.PLAY_MEDITATE_SOUND -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Playing meditation sounds")
                                        }
                                        FullIntent.STOP_SOUND -> {
                                            smartSpeak("Stopping audio")
                                        }
                                        FullIntent.SET_VOLUME -> {
                                            val level = fullResult.parameters["volume"] ?: "50"
                                            smartSpeak("Volume set to $level percent")
                                        }

                                        // ── TMR / LEARNING ──
                                        FullIntent.START_TMR_SESSION -> {
                                            navController.navigate("tmr_tools")
                                            smartSpeak("Starting your learning session")
                                        }
                                        FullIntent.GENERATE_FLASHCARDS -> {
                                            navController.navigate("tmr_tools")
                                            smartSpeak("Generating flashcards")
                                        }
                                        FullIntent.START_QUIZ -> {
                                            navController.navigate("tmr_tools")
                                            smartSpeak("Starting quiz mode")
                                        }

                                        // ── DEVICE CONTROL ──
                                        FullIntent.CONNECT_DEVICE -> {
                                            startActivity(android.content.Intent(this@MindSetActivity, BluetoothActivity::class.java))
                                            smartSpeak("Opening device connection")
                                        }
                                        FullIntent.DISCONNECT_DEVICE -> {
                                            smartSpeak("Disconnecting device")
                                        }
                                        FullIntent.SET_TEMPERATURE -> {
                                            val temp = fullResult.parameters["temperature"] ?: "22"
                                            smartSpeak("Setting temperature to $temp degrees")
                                        }
                                        FullIntent.TOGGLE_VIBRATION -> {
                                            smartSpeak("Toggling vibration")
                                        }
                                        FullIntent.TOGGLE_SENSOR -> {
                                            smartSpeak("Toggling sensor")
                                        }

                                        // ── NAVIGATION ──
                                        FullIntent.NAVIGATE_DASHBOARD -> {
                                            navController.navigate("dashboard")
                                            smartSpeak("Going to dashboard")
                                        }
                                        FullIntent.NAVIGATE_SLEEP_DATA,
                                        FullIntent.NAVIGATE_BIOFEEDBACK,
                                        FullIntent.NAVIGATE_REPORTS,
                                        FullIntent.NAVIGATE_DAILY_PROGRESS,
                                        FullIntent.SHOW_ANALYTICS,
                                        FullIntent.SHOW_DAILY_PROGRESS -> {
                                            navController.navigate("analytics")
                                            smartSpeak("Opening insights")
                                        }
                                        FullIntent.NAVIGATE_TODO -> {
                                            navController.navigate("todo_main")
                                            smartSpeak("Opening to-do list")
                                        }
                                        FullIntent.NAVIGATE_SETTINGS -> {
                                            navController.navigate("settings")
                                            smartSpeak("Opening settings")
                                        }
                                        FullIntent.NAVIGATE_PROFILE -> {
                                            startActivity(android.content.Intent(this@MindSetActivity, ProfileActivity::class.java))
                                            smartSpeak("Opening profile")
                                        }
                                        FullIntent.NAVIGATE_SOUND,
                                        FullIntent.NAVIGATE_ALARM,
                                        FullIntent.NAVIGATE_MORNING_BRIEF,
                                        FullIntent.START_BEDTIME_ROUTINE -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Opening sleep & sound")
                                        }
                                        FullIntent.NAVIGATE_TMR -> {
                                            navController.navigate("tmr_tools")
                                            smartSpeak("Opening TMR tools")
                                        }
                                        FullIntent.NAVIGATE_DREAM_JOURNAL -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Opening dream journal")
                                        }
                                        FullIntent.NAVIGATE_ANTIGRAVITY -> {
                                            startActivity(android.content.Intent(this@MindSetActivity, AntigravityActivity::class.java))
                                            smartSpeak("Opening antigravity visualization")
                                        }
                                        FullIntent.NAVIGATE_DEVICES -> {
                                            navController.navigate("dashboard")
                                            smartSpeak("Opening devices")
                                        }
                                        FullIntent.CHANGE_VOICE -> {
                                            startActivity(android.content.Intent(this@MindSetActivity, VoiceAgentActivity::class.java))
                                            smartSpeak("Opening voice selection")
                                        }
                                        FullIntent.IDENTIFY_USER -> {
                                            smartSpeak("Identifying your voice profile")
                                        }
                                        FullIntent.VOICE_SETTINGS -> {
                                            startActivity(android.content.Intent(this@MindSetActivity, VoiceAgentActivity::class.java))
                                            smartSpeak("Opening voice settings")
                                        }

                                        // ── BEDTIME ──
                                        FullIntent.ADD_BEDTIME_ITEM -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Adding to bedtime routine")
                                        }
                                        FullIntent.TOGGLE_BEDTIME_ITEM -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Updating bedtime routine")
                                        }

                                        // ── PROACTIVE ──
                                        FullIntent.WHATS_NEXT,
                                        FullIntent.MORNING_SUMMARY -> {
                                            navController.navigate("dashboard")
                                            smartSpeak("Let me check what's on your plate today")
                                        }
                                        FullIntent.GOODNIGHT -> {
                                            navController.navigate("bedtime")
                                            smartSpeak("Good night! Sleep well.")
                                        }

                                        // ── AI / DEVICE ──
                                        FullIntent.ASK_AI_QUESTION,
                                        FullIntent.START_RECOVERY_PLAN -> {
                                            navController.navigate("analytics")
                                            smartSpeak("Checking your AI insights")
                                        }
                                        FullIntent.SHOW_SAVED_REPORTS -> {
                                            navController.navigate("analytics")
                                            smartSpeak("Opening reports")
                                        }
                                        FullIntent.CHECK_BATTERY -> {
                                            smartSpeak("Checking device battery")
                                        }

                                        else -> {
                                            smartSpeak("Got it, working on that")
                                        }
                                    }
                                    } else {
                                        // ── Fallback for truly unrecognised input ──
                                        val deviceResult = VoiceCommandProcessor.processCommand(state.text)
                                        if (deviceResult.action != VoiceCommandProcessor.CommandAction.UNKNOWN) {
                                            executeDeviceCommand(deviceResult, navController)
                                            smartSpeak("Done")
                                        } else {
                                            smartSpeak("Sorry, I didn't understand that. Try saying 'Hey Vérité, add task' or 'play focus music'")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MindSetActivity", "Error processing voice command: ${e.message}", e)
                                    smartSpeak("I encountered an error processing that command")
                                }
                            }
                            is VoiceInputHandler.VoiceState.Error -> {
                                if (state.message.isNotEmpty()) {
                                    smartSpeak(state.message)
                                }
                            }
                            else -> {}
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route
                            NavigationBar(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = com.example.myapplication.ui.theme.AccentPrimary
                            ) {
                                NavigationItem.items.forEach { item ->
                                    NavigationBarItem(
                                        icon = { Icon(item.icon, contentDescription = item.title) },
                                        label = { Text(item.title) },
                                        selected = currentRoute == item.route,
                                        onClick = {
                                            notifViewModel.closePanel()
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = AccentPrimary,
                                            unselectedIconColor = TextMuted,
                                            indicatorColor = AccentPrimary.copy(alpha = 0.1f)
                                        )
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "dashboard",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("dashboard") {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToTaskDetail = { taskId -> navController.navigate("task_detail/$taskId") },
                                    onBackClick = { if (!navController.popBackStack()) finish() },
                                    onProfileClick = { navController.navigate("settings") },
                                    notificationCount = unreadCount,
                                    onNotificationClick = { notifViewModel.togglePanel() }
                                )
                            }
                            composable("todo_main") {
                                TodoMainScreen(
                                    viewModel = todoViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("task_detail/{taskId}") { backStackEntry ->
                                val taskId = backStackEntry.arguments?.getString("taskId")
                                val tasks by viewModel.allTasks.collectAsState()
                                val task = tasks.find { it.id == taskId }
                                if (task != null) {
                                    TaskDetailScreen(
                                        task = task,
                                        onUpdate = { updatedTask -> viewModel.updateTask(updatedTask) },
                                        onDelete = { viewModel.deleteTask(task.id); navController.popBackStack() },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable("analytics") {
                                AnalyticsScreen(
                                    viewModel = viewModel,
                                    onBackClick = { if (!navController.popBackStack()) finish() },
                                    onProfileClick = { navController.navigate("settings") },
                                    notificationCount = unreadCount,
                                    onNotificationClick = { notifViewModel.togglePanel() }
                                )
                            }
                            composable("bedtime") {
                                BedtimeRoutineScreen(
                                    viewModel = viewModel,
                                    onBackClick = { if (!navController.popBackStack()) finish() },
                                    onProfileClick = { navController.navigate("settings") },
                                    notificationCount = unreadCount,
                                    onNotificationClick = { notifViewModel.togglePanel() }
                                ) 
                            }
                            composable("settings") {
                                                SettingsScreen(
                                                    habitReminderHour = habitReminderHour,
                                                    bedtimeHour = bedtimeHour,
                                                    firebaseSyncEnabled = firebaseSyncEnabled,
                                                    llmFallbackEnabled = llmFallbackEnabled,
                                                    onHabitReminderChange = { settingsViewModel.updateHabitReminderHour(it) },
                                                    onBedtimeChange = { settingsViewModel.updateBedtimeHour(it) },
                                                    onFirebaseSyncToggle = { settingsViewModel.toggleFirebaseSync(it) },
                                                    onLlmFallbackToggle = { settingsViewModel.toggleLlmFallback(it) },
                                                    onExportData = { settingsViewModel.exportData() },
                                                    onImportData = { settingsViewModel.importData() },
                                                    onSeedDemoData = { settingsViewModel.seedDemoData() },
                                                    onClearAllData = { settingsViewModel.clearAllData() },
                                                    wakeWordEnabled = wakeWordEnabled,
                                                    onWakeWordToggle = { enabled ->
                                                        if (enabled && !hasAudioPermission()) {
                                                            requestPermissions()
                                                        } else {
                                                            settingsViewModel.toggleWakeWord(enabled)
                                                            toggleWakeWordService(enabled)
                                                        }
                                                    },
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }
                                            composable("tmr_tools") {
                                                VeriteNavGraph()
                                            }
                                        }
                    }

                    // ── Notification Panel Overlay ──────────────────
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPanelOpen,
                        enter = androidx.compose.animation.slideInVertically(
                            initialOffsetY = { -it }
                        ) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.slideOutVertically(
                            targetOffsetY = { -it }
                        ) + androidx.compose.animation.fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        NotificationPanel(
                            notifications = notifications,
                            onMarkAllRead = { notifViewModel.markAllAsRead() },
                            onClearAll = { notifViewModel.clearAll(); notifViewModel.closePanel() },
                            onNotificationClick = { notif ->
                                notifViewModel.markAsRead(notif.id)
                                notifViewModel.closePanel()
                                notif.actionRoute?.let { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onDismiss = { id -> notifViewModel.deleteNotification(id) },
                            onClose = { notifViewModel.closePanel() }
                        )
                    }

                    // ── Toast Banner (top of screen) ────────────────
                    VeriteToastBanner(
                        notification = toastNotification,
                        onDismiss = { toastNotification = null },
                        onClick = {
                            toastNotification?.actionRoute?.let { route ->
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                            toastNotification = null
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    } // End of Box overlay wrapper
                } // End of SkyBackground
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleVoiceIntent(intent)
    }

    private fun handleVoiceIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("ACTIVATE_VOICE", false) == true) {
            val command = intent.getStringExtra("VOICE_COMMAND")
            if (!command.isNullOrBlank()) {
                // Wake word already captured the command — process it directly
                _pendingVoiceCommand.value = command
                Log.i("MindSetActivity", "Wake word command received: $command")
            } else {
                // No command yet — start listening for one
                voiceInputHandler.startListening()
            }
        }
    }

    /**
     * Smart speak: uses ElevenLabs when enabled, falls back to device TTS.
     */
    private fun smartSpeak(text: String) {
        try {
            if (settingsManager.useElevenLabsTts) {
                val voiceId = settingsManager.selectedVoiceId
                if (voiceId.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            elevenLabsManager.speak(text, voiceId)
                        } catch (e: Exception) {
                            Log.e("MindSetActivity", "ElevenLabs speak failed, falling back to TTS", e)
                            withContext(Dispatchers.Main) {
                                voiceOutputHandler.speak(text)
                            }
                        }
                    }
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("MindSetActivity", "Error checking voice settings", e)
        }
        voiceOutputHandler.speak(text)
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun executeDeviceCommand(result: VoiceCommandProcessor.CommandResult, navController: androidx.navigation.NavController) {
        when (result.action) {
            VoiceCommandProcessor.CommandAction.NAVIGATE_AUTO_POWER_OFF -> navController.navigate("settings") // or specific route
            VoiceCommandProcessor.CommandAction.NAVIGATE_HELP -> navController.navigate("settings")
            VoiceCommandProcessor.CommandAction.NAVIGATE_QUICK_START -> navController.navigate("settings")
            VoiceCommandProcessor.CommandAction.NAVIGATE_DEVICES -> navController.navigate("dashboard")
            VoiceCommandProcessor.CommandAction.SET_TEMPERATURE -> {
                // Implementation for temperature control
            }
            VoiceCommandProcessor.CommandAction.TOGGLE_VIBRATION -> {
                // Implementation for vibration
            }
            else -> {}
        }
    }

    /**
     * Process a classified voice intent and execute the corresponding action.
     */
    private fun processVoiceIntent(
        fullResult: FullVoiceCommandProcessor.FullCommandResult,
        navController: androidx.navigation.NavController
    ) {
        val viewModel: DashboardViewModel? = null // handled by Compose, use navController only
        when (fullResult.intent) {
            FullIntent.START_SLEEP_SESSION -> {
                navController.navigate("bedtime")
                smartSpeak("Starting sleep session")
            }
            FullIntent.STOP_SLEEP_SESSION -> {
                navController.navigate("bedtime")
                smartSpeak("Stopping sleep tracking")
            }
            FullIntent.PLAY_SLEEP_SOUND -> {
                navController.navigate("bedtime")
                smartSpeak("Playing sleep sounds")
            }
            FullIntent.PLAY_FOCUS_SOUND -> {
                navController.navigate("bedtime")
                smartSpeak("Playing focus music")
            }
            FullIntent.NAVIGATE_DASHBOARD -> {
                navController.navigate("dashboard")
                smartSpeak("Going to dashboard")
            }
            FullIntent.NAVIGATE_TODO -> {
                navController.navigate("todo_main")
                smartSpeak("Opening to-do list")
            }
            FullIntent.NAVIGATE_SETTINGS -> {
                navController.navigate("settings")
                smartSpeak("Opening settings")
            }
            FullIntent.NAVIGATE_PROFILE -> {
                startActivity(android.content.Intent(this, ProfileActivity::class.java))
                smartSpeak("Opening profile")
            }
            FullIntent.CHANGE_VOICE, FullIntent.VOICE_SETTINGS -> {
                startActivity(android.content.Intent(this, VoiceAgentActivity::class.java))
                smartSpeak("Opening voice settings")
            }
            FullIntent.GOODNIGHT -> {
                navController.navigate("bedtime")
                smartSpeak("Good night! Sleep well.")
            }
            FullIntent.MORNING_SUMMARY, FullIntent.WHATS_NEXT -> {
                navController.navigate("dashboard")
                smartSpeak("Let me check what's on your plate today")
            }
            else -> {
                smartSpeak("Got it, working on that")
            }
        }
    }

    fun toggleWakeWordService(enabled: Boolean) {
        try {
            if (enabled && !hasAudioPermission()) return
            
            val serviceIntent = android.content.Intent(this, com.example.myapplication.utils.VeriteWakeWordService::class.java)
            if (enabled) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                stopService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MindSetActivity", "Error toggling wake word service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputHandler.destroy()
        voiceOutputHandler.destroy()
    }
}

sealed class NavigationItem(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : NavigationItem("dashboard", "Home", Icons.Default.Dashboard)
    object Analytics : NavigationItem("analytics", "Insights", Icons.Default.Analytics)
    object Bedtime : NavigationItem("bedtime", "Sleep", Icons.Default.Bedtime)
    object Settings : NavigationItem("settings", "Settings", Icons.Default.Settings)

    companion object {
        val items = listOf(Dashboard, Analytics, Bedtime, Settings)
    }
}
