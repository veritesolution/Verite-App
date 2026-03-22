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
import com.example.myapplication.ui.components.PieChartView
import kotlinx.coroutines.launch

class OrynSummaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oryn_summary)

        setupUI()
        loadChartData()
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
        
        // Format Keys Legend
        setupKey(findViewById(R.id.key1), Color.parseColor("#E0E0E0"), "health")
        setupKey(findViewById(R.id.key2), Color.parseColor("#808080"), "health")
        setupKey(findViewById(R.id.key3), Color.parseColor("#406060"), "health")
        setupKey(findViewById(R.id.key4), Color.parseColor("#298080"), "health")
        
        // Feedback Button
        findViewById<Button>(R.id.btnFeedbackSettings).setOnClickListener {
            startActivity(Intent(this, FeedbackSettingsActivity::class.java))
        }
    }
    
    private fun setupKey(keyView: android.view.View, colorInt: Int, label: String) {
        val colorDot = keyView.findViewById<android.view.View>(R.id.colorDot)
        val tvLabel = keyView.findViewById<TextView>(R.id.tvKeyName)
        
        colorDot.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
        tvLabel.text = label
    }

    private fun loadChartData() {
        val pieChartView = findViewById<PieChartView>(R.id.pieChartView)
        
        val slices = listOf(
            PieChartView.Slice(value = 50f, color = Color.parseColor("#A1BCB9")), // Lightest Gray/Green
            PieChartView.Slice(value = 25f, color = Color.parseColor("#808483")), // Mid Gray
            PieChartView.Slice(value = 15f, color = Color.parseColor("#1B292C")), // Very Dark
            PieChartView.Slice(value = 10f, color = Color.parseColor("#206764"))  // Teal
        )
        
        pieChartView.setSlices(slices)
    }
}
