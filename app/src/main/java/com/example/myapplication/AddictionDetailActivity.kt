package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddictionDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addiction_detail)

        val addictionType = intent.getStringExtra("ADDICTION_TYPE") ?: "Unknown"

        findViewById<android.view.View>(R.id.backButton).setOnClickListener {
            finish()
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

            val intent = Intent(this, AiPlanActivity::class.java).apply {
                putExtra("ADDICTION_TYPE", addictionType)
                putExtra("FREQUENCY", frequency)
                putExtra("DURATION", duration)
                putExtra("TRIGGER", trigger)
                putExtra("MOTIVATION", motivation)
            }
            startActivity(intent)
        }
    }
}
