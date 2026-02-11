package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Back Button
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Edit Profile
        findViewById<View>(R.id.btnEditProfile).setOnClickListener {
            Toast.makeText(this, "Edit Profile Clicked", Toast.LENGTH_SHORT).show()
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
