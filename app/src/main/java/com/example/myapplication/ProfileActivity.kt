package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.myapplication.data.auth.AuthManager
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.ui.screens.ProfileScreen
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.utils.SettingsManager
import com.example.myapplication.utils.VeriteWakeWordService

class ProfileActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordPermission) {
            settingsManager.wakeWordEnabled = true
            isWakeWordEnabledState = true
            toggleWakeWordService(true)
        }
    }

    // Keep state locally for the UI to observe seamlessly
    private var isWakeWordEnabledState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        isWakeWordEnabledState = settingsManager.wakeWordEnabled

        val database = AppDatabase.getDatabase(applicationContext)

        setContent {
            VeriteTheme {
                // Collect DB purely in Compose state
                val userState by database.userDao().getUser().collectAsState(initial = null)
                
                ProfileScreen(
                    userName = userState?.name ?: "User",
                    userEmail = userState?.email ?: "user@example.com",
                    profileImagePath = userState?.profileImagePath,
                    isWakeWordEnabled = isWakeWordEnabledState,
                    onBack = { finish() },
                    onEditProfile = {
                        startActivity(Intent(this@ProfileActivity, EditProfileActivity::class.java))
                    },
                    onAccount = {
                        startActivity(Intent(this@ProfileActivity, EditProfileActivity::class.java))
                    },
                    onAppearance = {
                        startActivity(Intent(this@ProfileActivity, AppearanceActivity::class.java))
                    },
                    onLanguage = {
                        startActivity(Intent(this@ProfileActivity, LanguageActivity::class.java))
                    },
                    onNotifications = {
                        startActivity(Intent(this@ProfileActivity, NotificationsActivity::class.java))
                    },
                    onPrivacy = {
                        startActivity(Intent(this@ProfileActivity, PrivacyActivity::class.java))
                    },
                    onVoiceAgent = {
                        startActivity(Intent(this@ProfileActivity, VoiceAgentActivity::class.java))
                    },
                    onWakeWordToggle = { isChecked ->
                        if (isChecked) {
                            if (hasAudioPermission()) {
                                settingsManager.wakeWordEnabled = true
                                isWakeWordEnabledState = true
                                toggleWakeWordService(true)
                            } else {
                                requestPermissions()
                            }
                        } else {
                            settingsManager.wakeWordEnabled = false
                            isWakeWordEnabledState = false
                            toggleWakeWordService(false)
                        }
                    },
                    onLogout = {
                        AuthManager(this@ProfileActivity).signOut()
                        val intent = Intent(this@ProfileActivity, WelcomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
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

    private fun toggleWakeWordService(enabled: Boolean) {
        try {
            val serviceIntent = Intent(this, VeriteWakeWordService::class.java)
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
            Log.e("ProfileActivity", "Error toggling wake word service", e)
        }
    }
}
