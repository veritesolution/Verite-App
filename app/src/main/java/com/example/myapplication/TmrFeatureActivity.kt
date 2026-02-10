package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TmrFeatureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tmr_feature)

        // Color the Title "Vérité"
        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        val textStr = "Vérité"
        val s = SpannableString(textStr)
        s.setSpan(ForegroundColorSpan(Color.WHITE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(Color.parseColor("#00BFA5")), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(Color.WHITE), 2, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        headerTitle.text = s

        // Back Button
        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Navigation Buttons
        findViewById<View>(R.id.btnLearning).setOnClickListener {
            startActivity(Intent(this, LearningSessionActivity::class.java))
        }

        findViewById<View>(R.id.btnAddiction).setOnClickListener {
            startActivity(Intent(this, AddictionCategoryActivity::class.java))
        }

        findViewById<View>(R.id.btnSavedReports).setOnClickListener {
            startActivity(Intent(this, SavedReportsActivity::class.java))
        }
    }
}
