package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.bluetooth.BluetoothLeManager
import com.example.myapplication.data.logic.StressDetectionEngine
import com.example.myapplication.data.logic.StressLevel
import kotlinx.coroutines.launch

class BioFeedbackActivity : AppCompatActivity() {

    private lateinit var tvHeartRate: TextView
    private lateinit var tvStressValue: TextView
    private lateinit var stressProgress: ProgressBar
    private lateinit var tvAlpha: TextView
    private lateinit var tvBeta: TextView
    private lateinit var tvTheta: TextView
    
    private lateinit var bluetoothLeManager: BluetoothLeManager
    private val stressDetectionEngine = StressDetectionEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_feedback)

        bluetoothLeManager = BluetoothLeManager.getInstance(this)

        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvStressValue = findViewById(R.id.tvStressValue)
        stressProgress = findViewById(R.id.stressProgress)
        tvAlpha = findViewById(R.id.tvAlpha)
        tvBeta = findViewById(R.id.tvBeta)
        tvTheta = findViewById(R.id.tvTheta)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        // Start observing data
        observeBioData()
        observeStressLevels()
    }

    private fun observeBioData() {
        lifecycleScope.launch {
            bluetoothLeManager.bioDataStream.collect { data ->
                // Update raw UI
                tvHeartRate.text = data.heartRate.toString()
                tvAlpha.text = String.format("%.1f", data.alpha)
                tvBeta.text = String.format("%.1f", data.beta)
                tvTheta.text = String.format("%.1f", data.theta)
                
                // Pass to engine for analysis
                stressDetectionEngine.analyze(data)
            }
        }
    }

    private fun observeStressLevels() {
        lifecycleScope.launch {
            stressDetectionEngine.currentStress.collect { state ->
                stressProgress.progress = state.score
                
                val levelText = when(state.level) {
                    StressLevel.VERY_LOW -> "Very Low"
                    StressLevel.LOW -> "Low"
                    StressLevel.MODERATE -> "Moderate"
                    StressLevel.HIGH -> "High"
                }
                
                tvStressValue.text = "$levelText (${state.score}%)"
            }
        }
    }
}
