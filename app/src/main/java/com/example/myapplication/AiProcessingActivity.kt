package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AiProcessingActivity : AppCompatActivity() {

    private lateinit var tvDayCounter: TextView
    private var currentDay = 1
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_processing)

        tvDayCounter = findViewById(R.id.tvDayCounter)
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        startProcessing()
    }

    private fun startProcessing() {
        val runnable = object : Runnable {
            override fun run() {
                if (currentDay <= 21) {
                    tvDayCounter.text = "Optimizing Day $currentDay..."
                    currentDay++
                    handler.postDelayed(this, 150L) // Fast animation for 21 days
                } else {
                    navigateToPlan()
                }
            }
        }
        handler.post(runnable)
    }

    private fun navigateToPlan() {
        val intent = Intent(this, AiPlanActivity::class.java).apply {
            // Forward all extras from AddictionDetail
            putExtras(this@AiProcessingActivity.intent)
        }
        startActivity(intent)
        finish()
    }
}
