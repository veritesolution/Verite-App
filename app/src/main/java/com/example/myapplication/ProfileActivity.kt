package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.myapplication.data.auth.AuthManager
import com.example.myapplication.utils.SettingsManager
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordPermission) {
            settingsManager.wakeWordEnabled = true
            // Refresh the UI switch if needed (re-setup)
            setupSoundMenu()
            toggleWakeWordService(true)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        setContentView(R.layout.activity_profile)

        findViewById<ComposeView>(R.id.composeView).setContent {
            VeriteTheme {
                SkyBackground { }
            }
        }

        // Back Button
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Branded header logo
        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        headerTitle?.let { 
            com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(it)
        }

        val tvName = findViewById<TextView>(R.id.txtUserName)
        val tvEmail = findViewById<TextView>(R.id.txtUserEmail)
        val imgProfile = findViewById<ImageView>(R.id.imgProfileLarge)

        // Load User Data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val db = AppDatabase.getDatabase(applicationContext)
                db.userDao().getUser().collect { user ->
                    if (user != null) {
                        tvName.text = user.name
                        tvEmail.text = user.email
                        if (!user.profileImagePath.isNullOrEmpty()) {
                            val file = java.io.File(user.profileImagePath!!)
                            if (file.exists()) {
                                imgProfile.load(file) {
                                    transformations(CircleCropTransformation())
                                }
                            }
                        }
                    } else {
                        tvName.text = "User"
                        tvEmail.text = "user@example.com"
                        imgProfile.setImageResource(R.drawable.user)
                    }
                }
            }
        }

        // Edit Profile
        findViewById<View>(R.id.btnEditProfile).setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }

        // Setup Menu Items
        setupMenuItem(R.id.menuAccount, R.drawable.ic_account, "Account") {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }
        setupMenuItem(R.id.menuAppearance, R.drawable.ic_appearance, "Appearance") {
            startActivity(Intent(this, AppearanceActivity::class.java))
        }
        setupMenuItem(R.id.menuLanguage, R.drawable.ic_language, "Language") {
            startActivity(Intent(this, LanguageActivity::class.java))
        }
        setupMenuItem(R.id.menuNotifications, R.drawable.ic_notifications, "Notifications") {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        setupMenuItem(R.id.menuPrivacy, R.drawable.ic_privacy, "Privacy & Security") {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }
        
        setupSoundMenu()

        // Log Out
        findViewById<View>(R.id.btnLogout).setOnClickListener {
            AuthManager(this).signOut()
            // Navigate to Welcome and clear stack
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupSoundMenu() {
        setupMenuItem(
            R.id.menuSound,
            R.drawable.ic_sound,
            "Wake-Word Listener",
            showSwitch = true,
            switchChecked = settingsManager.wakeWordEnabled,
            onCheckedChange = { isChecked ->
                if (isChecked) {
                    if (hasAudioPermission()) {
                        settingsManager.wakeWordEnabled = true
                        toggleWakeWordService(true)
                    } else {
                        // Uncheck until permission granted
                        requestPermissions()
                    }
                } else {
                    settingsManager.wakeWordEnabled = false
                    toggleWakeWordService(false)
                }
            }
        )
    }

    private fun setupMenuItem(
        viewId: Int,
        iconResId: Int,
        title: String,
        showSwitch: Boolean = false,
        switchChecked: Boolean = false,
        onCheckedChange: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ) {
        val menuItem = findViewById<View>(viewId)
        val icon = menuItem.findViewById<ImageView>(R.id.menuIcon)
        val text = menuItem.findViewById<TextView>(R.id.menuTitle)
        val switch = menuItem.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.menuSwitch)
        val arrow = menuItem.findViewById<ImageView>(R.id.menuArrow)

        icon.setImageResource(iconResId)
        text.text = title

        if (showSwitch) {
            switch.visibility = View.VISIBLE
            arrow.visibility = View.GONE
            switch.isChecked = switchChecked
            switch.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange?.invoke(isChecked)
            }
            // Also allow clicking the whole item to toggle the switch
            menuItem.setOnClickListener {
                switch.toggle()
            }
        } else {
            switch.visibility = View.GONE
            arrow.visibility = View.VISIBLE
            menuItem.setOnClickListener {
                if (onClick != null) {
                    onClick()
                } else {
                    Toast.makeText(this, "$title Clicked", Toast.LENGTH_SHORT).show()
                }
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
            val serviceIntent = Intent(this, com.example.myapplication.utils.VeriteWakeWordService::class.java)
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
