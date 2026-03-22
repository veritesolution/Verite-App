package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.verite.tmr.DocumentTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearningSessionActivity : AppCompatActivity() {

    private val documentPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            processDocument(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_session)

        // Init PDFBox
        DocumentTextExtractor.init(applicationContext)

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

        // Add Document button
        findViewById<TextView>(R.id.btnAddDocument).setOnClickListener {
            // Pick PDF or DOCX using the generic */* allowing DocumentTextExtractor to filter
            documentPickerLauncher.launch("*/*")
        }

        // Study Materials button
        findViewById<LinearLayout>(R.id.btnStudyMaterials).setOnClickListener {
            Toast.makeText(this, "Opening study materials...", Toast.LENGTH_SHORT).show()
        }

        // + button at bottom - goes back to TMR Feature Page
        findViewById<ImageView>(R.id.btnSkip).setOnClickListener {
            finish()
        }
    }

    private fun processDocument(uri: Uri) {
        Toast.makeText(this, "Extracting text...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val extractedText = withContext(Dispatchers.IO) {
                DocumentTextExtractor.extractText(this@LearningSessionActivity, uri)
            }
            if (extractedText.isNullOrBlank()) {
                Toast.makeText(this@LearningSessionActivity, "Could not extract text from document.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@LearningSessionActivity, "Text extracted (${extractedText.length} chars). Analyzing...", Toast.LENGTH_SHORT).show()
                // Save for LearningSkillsActivity to reuse
                getSharedPreferences("tmr_session", MODE_PRIVATE).edit()
                    .putString("last_processed_text", extractedText)
                    .apply()
                val intent = Intent(this@LearningSessionActivity, StudyMaterialActivity::class.java).apply {
                    putExtra("EXTRA_TEXT", extractedText)
                }
                startActivity(intent)
            }
        }
    }
}
