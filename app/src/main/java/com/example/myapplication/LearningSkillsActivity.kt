package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LearningSkillsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_skills)

        // Back button
        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Profile icon
        findViewById<ImageView>(R.id.profileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Branded header logo
        val headerTitle = findViewById<android.widget.TextView>(R.id.headerTitle)
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(headerTitle)

        // Get last processed text from shared preferences
        val prefs = getSharedPreferences("tmr_session", MODE_PRIVATE)
        val lastText = prefs.getString("last_processed_text", null)

        // Strengthen Memories → Concepts tab
        findViewById<android.widget.TextView>(R.id.btnStrengthen).setOnClickListener {
            launchStudyMaterial(lastText, "concepts", "Strengthen Memories")
        }

        // TMR Audio Cues → Audio generation
        findViewById<android.widget.TextView>(R.id.btnAudioCues).setOnClickListener {
            launchStudyMaterial(lastText, "concepts", "TMR Audio Cues")
        }

        // Reinforce Positive Thoughts → Flashcards tab
        findViewById<android.widget.TextView>(R.id.btnReinforce).setOnClickListener {
            launchStudyMaterial(lastText, "flashcards", "Reinforce Thoughts")
        }

        // Make Behavioural Actions → Quiz tab
        findViewById<android.widget.TextView>(R.id.btnBehavioural).setOnClickListener {
            launchStudyMaterial(lastText, "quiz", "Behavioural Actions")
        }

        // SKIP - returns to TMR Feature page
        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            finish()
        }
    }

    private fun launchStudyMaterial(text: String?, initialTab: String, featureName: String) {
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Please upload a study document first from Study Material page", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, StudyMaterialActivity::class.java).apply {
            putExtra("EXTRA_TEXT", text)
            putExtra("INITIAL_TAB", initialTab)
        }
        startActivity(intent)
    }
}
