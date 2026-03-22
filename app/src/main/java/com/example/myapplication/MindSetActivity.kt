package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
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
import com.example.myapplication.ml.LlmVoiceProcessor
import com.example.myapplication.ui.tasks.TaskDetailScreen
import com.example.myapplication.ui.todo.TodoMainScreen
import com.example.myapplication.viewmodel.TodoViewModel
import com.example.myapplication.ui.settings.SettingsViewModel
import com.example.myapplication.data.model.VoiceCommandResult
import com.example.myapplication.data.model.Intent
import com.example.myapplication.data.model.Task

import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.TextMuted
import androidx.activity.viewModels

class MindSetActivity : ComponentActivity() {

    private lateinit var voiceInputHandler: VoiceInputHandler
    private lateinit var voiceOutputHandler: VoiceOutputHandler

    private val settingsViewModel: SettingsViewModel by viewModels()

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
                    // Use the class-level settingsViewModel instead of a local one
                    
                    val voiceState by voiceInputHandler.state.collectAsState()
                    var partialText by remember { mutableStateOf("") }
                    var lastResult by remember { mutableStateOf<VoiceCommandResult?>(null) }
                    
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
                                // 1. Try new task/habit parser
                                val taskResult = voiceInputHandler.parseCommand(state.text)
                                if (taskResult.intent != Intent.UNKNOWN && taskResult.confidence > 0.7f) {
                                    lastResult = taskResult
                                } else {
                                    // 2. Try legacy device parser
                                    val logTag = "MindSetActivity"
                                    val deviceResult = VoiceCommandProcessor.processCommand(state.text)
                                    if (deviceResult.action != VoiceCommandProcessor.CommandAction.UNKNOWN) {
                                        executeDeviceCommand(deviceResult, navController)
                                        voiceOutputHandler.speak("Executing device command")
                                        lastResult = null // Handled locally
                                    } else if (llmFallbackEnabled) {
                                        // 3. Fallback to LLM
                                        val llm = LlmVoiceProcessor(BuildConfig.OPENROUTER_API_KEY)
                                        val fallback = llm.classify(state.text)
                                        if (fallback != null) lastResult = fallback
                                    }
                                }
                                
                                lastResult?.let { result ->
                                    viewModel.executeVoiceCommand(result)
                                    voiceOutputHandler.speak("Executing: ${result.intent}")
                                }
                            }
                            is VoiceInputHandler.VoiceState.Error -> {
                                if (state.message.isNotEmpty()) {
                                    voiceOutputHandler.speak(state.message)
                                }
                            }
                            else -> {}
                        }
                    }

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
                                    onProfileClick = { navController.navigate("settings") }
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
                                    onProfileClick = { navController.navigate("settings") }
                                ) 
                            }
                            composable("bedtime") { 
                                BedtimeRoutineScreen(
                                    viewModel = viewModel,
                                    onBackClick = { if (!navController.popBackStack()) finish() },
                                    onProfileClick = { navController.navigate("settings") }
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
                        }
                    }
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
            voiceInputHandler.startListening()
        }
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
