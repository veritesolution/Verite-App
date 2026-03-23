package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.bluetooth.BluetoothLeManager
import com.example.myapplication.data.logic.StressDetectionEngine
import com.example.myapplication.data.logic.StressLevel
import com.example.myapplication.data.remote.AudioPredictionManager
import com.example.myapplication.ui.components.WaveformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Button
import android.view.View

class BioFeedbackActivity : AppCompatActivity() {

    private lateinit var tvHeartRate: TextView
    private lateinit var tvStressValue: TextView
    private lateinit var stressProgress: ProgressBar
    private lateinit var tvAlpha: TextView
    private lateinit var tvBeta: TextView
    private lateinit var tvTheta: TextView
    private lateinit var eegChart: WaveformView
    private lateinit var emgChart: WaveformView

    // AI Prediction Views
    private lateinit var btnRunAiDiagnostic: Button
    private lateinit var tvAiResult: TextView
    private lateinit var aiLoadingProgress: ProgressBar

    private lateinit var bluetoothLeManager: BluetoothLeManager
    private val stressDetectionEngine = StressDetectionEngine()
    private val audioPredictionManager = AudioPredictionManager()

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
        eegChart = findViewById(R.id.eegChart)
        emgChart = findViewById(R.id.emgChart)
        
        btnRunAiDiagnostic = findViewById(R.id.btnRunAiDiagnostic)
        tvAiResult = findViewById(R.id.tvAiResult)
        aiLoadingProgress = findViewById(R.id.aiLoadingProgress)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.profileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Start observing live data
        observeBioData()
        observeStressLevels()
        setupAiDiagnostic()
    }

    private fun setupAiDiagnostic() {
        btnRunAiDiagnostic.setOnClickListener {
            // Note: This requires a real audio file to exist at this path.
            // For production, integrate with Android's MediaRecorder to generate this file first.
            val audioFilePath = android.os.Environment.getExternalStorageDirectory().path + "/Download/my_recorded_audio.mp3"
            
            btnRunAiDiagnostic.isEnabled = false
            aiLoadingProgress.visibility = View.VISIBLE
            tvAiResult.text = "Uploading and analyzing audio..."
            tvAiResult.setTextColor(Color.parseColor("#88FFFFFF"))

            lifecycleScope.launch {
                val result = audioPredictionManager.uploadAudioFile(audioFilePath)
                withContext(Dispatchers.Main) {
                    aiLoadingProgress.visibility = View.GONE
                    btnRunAiDiagnostic.isEnabled = true
                    tvAiResult.text = result
                    
                    if (result.startsWith("Error") || result.startsWith("Network")) {
                        tvAiResult.setTextColor(Color.parseColor("#FF5252")) // Red for error
                    } else {
                        tvAiResult.setTextColor(Color.parseColor("#4CAF50")) // Green for success
                    }
                }
            }
        }
    }

    private fun observeBioData() {
        lifecycleScope.launch {
            bluetoothLeManager.bioDataStream.collect { data ->
                tvHeartRate.text = data.heartRate.toString()
                tvAlpha.text = String.format(java.util.Locale.US, "%.1f", data.alpha)
                tvBeta.text = String.format(java.util.Locale.US, "%.1f", data.beta)
                tvTheta.text = String.format(java.util.Locale.US, "%.1f", data.theta)
                // Pass to engine for analysis
                stressDetectionEngine.analyze(data)
            }
        }
    }

    private fun observeStressLevels() {
        lifecycleScope.launch {
            stressDetectionEngine.currentStress.collect { state ->
                stressProgress.progress = state.score
                val levelText = when (state.level) {
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
