package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class BioFeedbackActivity : AppCompatActivity() {

    private lateinit var tvHeartRate: TextView
    private lateinit var tvStressValue: TextView
    private lateinit var stressProgress: ProgressBar
    private lateinit var tvAlpha: TextView
    private lateinit var tvBeta: TextView
    private lateinit var tvTheta: TextView
    
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()

    private val dataUpdater = object : Runnable {
        override fun run() {
            updateData()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_feedback)

        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvStressValue = findViewById(R.id.tvStressValue)
        stressProgress = findViewById(R.id.stressProgress)
        tvAlpha = findViewById(R.id.tvAlpha)
        tvBeta = findViewById(R.id.tvBeta)
        tvTheta = findViewById(R.id.tvTheta)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        handler.post(dataUpdater)
    }

    private fun updateData() {
        // Heart Rate: 65 - 85
        val hr = 65 + random.nextInt(20)
        tvHeartRate.text = hr.toString()

        // Stress: 20 - 60%
        val stress = 20 + random.nextInt(40)
        stressProgress.progress = stress
        val stressLevel = when {
            stress < 30 -> "Very Low"
            stress < 45 -> "Low"
            stress < 60 -> "Moderate"
            else -> "High"
        }
        tvStressValue.text = "$stressLevel ($stress%)"

        // Brain Waves
        tvAlpha.text = String.format("%.1f", 10 + random.nextFloat() * 5)
        tvBeta.text = String.format("%.1f", 15 + random.nextFloat() * 8)
        tvTheta.text = String.format("%.1f", 4 + random.nextFloat() * 4)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(dataUpdater)
    }
}
