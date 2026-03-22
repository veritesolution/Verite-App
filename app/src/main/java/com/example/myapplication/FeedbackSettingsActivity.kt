package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplication.data.local.AppDatabase
import kotlinx.coroutines.launch

class FeedbackSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback_settings)

        setupUI()
    }

    private fun setupUI() {
        val database = AppDatabase.getDatabase(this)
        
        // Header
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        
        val profileIcon = findViewById<ImageView>(R.id.profileIcon)
        profileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        
        // Load User Avatar
        lifecycleScope.launch {
            database.userDao().getUser().collect { user ->
                user?.profileImagePath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        profileIcon.clearColorFilter()
                        profileIcon.load(file) {
                            transformations(CircleCropTransformation())
                        }
                    }
                }
            }
        }

        // Branded header
        val tvAppTitle = findViewById<TextView>(R.id.headerTitle)
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(tvAppTitle)
        
        // Confirm Button Logic - Navigate to Home/Dashboard
        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            // Clearing the stack and returning to the main dashboard
            val intent = Intent(this, DeviceDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
