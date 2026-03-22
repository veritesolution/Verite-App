package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class AdaptiveSoundActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adaptive_sound)

        // Branded header
        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        headerTitle?.let {
            com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(it)
        }

        // Back
        findViewById<android.view.View>(R.id.backButton)?.setOnClickListener { finish() }

        // Profile
        findViewById<android.view.View>(R.id.profileIcon)?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Mode cards navigation - Using View to avoid ClassCastException (XML uses LinearLayout)
        findViewById<android.view.View>(R.id.cardAiAuto)?.setOnClickListener {
            startActivity(Intent(this, AiAutoSoundActivity::class.java))
        }

        findViewById<android.view.View>(R.id.cardFocus)?.setOnClickListener {
            val intent = Intent(this, SoundExplorerActivity::class.java)
            intent.putExtra("CATEGORY", "Focus")
            startActivity(intent)
        }

        findViewById<android.view.View>(R.id.cardRelax)?.setOnClickListener {
            val intent = Intent(this, SoundExplorerActivity::class.java)
            intent.putExtra("CATEGORY", "Relax")
            startActivity(intent)
        }

        findViewById<android.view.View>(R.id.cardSleep)?.setOnClickListener {
            val intent = Intent(this, SoundExplorerActivity::class.java)
            intent.putExtra("CATEGORY", "Sleep")
            startActivity(intent)
        }

        findViewById<android.view.View>(R.id.cardMeditate)?.setOnClickListener {
            val intent = Intent(this, SoundExplorerActivity::class.java)
            intent.putExtra("CATEGORY", "Meditate")
            startActivity(intent)
        }

        setupAiTicker()

        // Bottom search bar
        findViewById<TextView>(R.id.btnAudioSearch)?.setOnClickListener {
            startActivity(Intent(this, AudioSearchActivity::class.java))
        }
    }

    private fun setupAiTicker() {
        try {
            val ticker = findViewById<TextView>(R.id.recommendationTicker) ?: return
            ticker.isSelected = true // Enable marquee

            val tips = listOf(
                "AI Tip: High Beta detected. Switch to Focus Mode to optimize your flow state.",
                "AI Tip: Stress levels rising. Alpha waves recommended for immediate recovery.",
                "AI Tip: Heart rate stabilizing. Perfect time for a deep meditation session.",
                "AI Tip: Autonomous Mode can manage your binaural flow for you.",
                "AI Tip: Deep Sleep induction is most effective when Theta cycles begin."
            )

            var currentTipIndex = 0
            ticker.post(object : Runnable {
                override fun run() {
                    ticker.text = tips[currentTipIndex]
                    currentTipIndex = (currentTipIndex + 1) % tips.size
                    ticker.postDelayed(this, 8000)
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("AdaptiveSound", "Ticker setup failed: ${e.message}")
        }
    }
}
