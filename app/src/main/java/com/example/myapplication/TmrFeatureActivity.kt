package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplication.util.ProfileIconHelper
import kotlinx.coroutines.launch

class TmrFeatureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tmr_feature)

        // No ComposeView needed - pure XML layout

        // Apply branded Vérité logo style
        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(headerTitle)

        // Back Button
        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Navigation Buttons
        findViewById<View>(R.id.btnLearning).setOnClickListener {
            startActivity(Intent(this, LearningSkillsActivity::class.java))
        }

        findViewById<View>(R.id.btnAilment).setOnClickListener {
            startActivity(Intent(this, AilmentCategoryActivity::class.java))
        }

        findViewById<View>(R.id.btnSavedReports).setOnClickListener {
            startActivity(Intent(this, LearningSessionActivity::class.java))
        }

        // Profile Icon correctly synced
        val profileIcon = findViewById<ImageView>(R.id.profileIcon)
        profileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        ProfileIconHelper.syncProfileIcon(this, profileIcon)
    }
}
