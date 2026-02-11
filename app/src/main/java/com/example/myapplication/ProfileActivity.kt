package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplication.data.local.AppDatabase
import kotlinx.coroutines.flow.collect

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Back Button
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val tvName = findViewById<TextView>(R.id.txtUserName)
        val tvEmail = findViewById<TextView>(R.id.txtUserEmail)
        val imgProfile = findViewById<ImageView>(R.id.imgProfileLarge)

        // Load User Data
        lifecycleScope.launchWhenResumed {
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
                }
            }
        }

        // Edit Profile
        findViewById<View>(R.id.btnEditProfile).setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }

        // Setup Menu Items
        setupMenuItem(R.id.menuAccount, R.drawable.ic_account, "Account")
        setupMenuItem(R.id.menuAppearance, R.drawable.ic_appearance, "Appearance")
        setupMenuItem(R.id.menuLanguage, R.drawable.ic_language, "Language")
        setupMenuItem(R.id.menuNotifications, R.drawable.ic_notifications, "Notifications")
        setupMenuItem(R.id.menuPrivacy, R.drawable.ic_privacy, "Privacy & Security")
        setupMenuItem(R.id.menuSound, R.drawable.ic_sound, "Sound")

        // Log Out
        findViewById<View>(R.id.btnLogout).setOnClickListener {
            // Navigate to Welcome and clear stack
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupMenuItem(viewId: Int, iconResId: Int, title: String) {
        val menuItem = findViewById<View>(viewId)
        val icon = menuItem.findViewById<ImageView>(R.id.menuIcon)
        val text = menuItem.findViewById<TextView>(R.id.menuTitle)

        icon.setImageResource(iconResId)
        text.text = title

        menuItem.setOnClickListener {
            Toast.makeText(this, "$title Clicked", Toast.LENGTH_SHORT).show()
        }
    }
}
