package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme

class AilmentDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ailment_detail)

        findViewById<ComposeView>(R.id.composeView).setContent {
            VeriteTheme {
                SkyBackground { }
            }
        }

        val ailmentType = intent.getStringExtra("AILMENT_TYPE") ?: "Unknown"

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<android.view.View>(R.id.profileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<Button>(R.id.btnGenerate).setOnClickListener {
            val frequency = findViewById<EditText>(R.id.inputFrequency).text.toString()
            val duration = findViewById<EditText>(R.id.inputDuration).text.toString()
            val trigger = findViewById<EditText>(R.id.inputTrigger).text.toString()
            val motivation = findViewById<EditText>(R.id.inputMotivation).text.toString()

            if (frequency.isBlank() || duration.isBlank() || trigger.isBlank() || motivation.isBlank()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, AiProcessingActivity::class.java).apply {
                putExtra("AILMENT_TYPE", ailmentType)
                putExtra("FREQUENCY", frequency)
                putExtra("DURATION", duration)
                putExtra("TRIGGER", trigger)
                putExtra("MOTIVATION", motivation)
            }
            startActivity(intent)
        }
    }
}
