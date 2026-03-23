package com.example.myapplication

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerControlView
import com.example.myapplication.data.audio.BinauralAudioManager
import com.example.myapplication.data.audio.SoundType
import com.example.myapplication.data.bluetooth.BioData
import com.example.myapplication.data.bluetooth.BluetoothLeManager
import com.example.myapplication.data.logic.StressDetectionEngine
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AiAutoSoundActivity : AppCompatActivity() {

    private lateinit var audioManager: BinauralAudioManager
    private val stressDetectionEngine = StressDetectionEngine()
    private val bluetoothLeManager by lazy { BluetoothLeManager.getInstance(this) }
    
    private lateinit var logContainer: LinearLayout
    private lateinit var statusTv: TextView
    private lateinit var recommendationTv: TextView
    private lateinit var aiReasoningTv: TextView
    
    private var isAutonomous = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val composeBackground = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setContent { VeriteTheme { SkyBackground { } } }
        }
        rootFrame.addView(composeBackground)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(20), dpToPx(32), dpToPx(20), dpToPx(40))
        }
        scrollView.addView(root)

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        header.addView(ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setOnClickListener { finish() }
        })
        val titleTv = TextView(this).apply {
            text = "🧠  AI Auto-Adaptive Mode"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dpToPx(12) }
        }
        header.addView(titleTv)
        root.addView(header)

        // AI Status Card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(20).toFloat()
                setColor(Color.parseColor("#1A2020"))
                setStroke(dpToPx(2), Color.parseColor("#00BFA5"))
            }
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dpToPx(24); bottomMargin = dpToPx(20) }
        }

        statusTv = TextView(this).apply {
            text = "System Status: IDLE"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00BFA5"))
        }
        statusCard.addView(statusTv)

        recommendationTv = TextView(this).apply {
            text = "Waiting for bio-data synchronization..."
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dpToPx(12) }
        }
        statusCard.addView(recommendationTv)

        aiReasoningTv = TextView(this).apply {
            text = "The AI is currently monitoring your brain waves and heart rate variability to determine the optimal frequency for your current state."
            textSize = 13f
            setTextColor(Color.parseColor("#AAFFFF"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dpToPx(8) }
        }
        statusCard.addView(aiReasoningTv)
        root.addView(statusCard)

        // Start/Stop Button
        val startBtn = TextView(this).apply {
            text = "🚀  Enable AI Autonomy"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#004D5A"), Color.parseColor("#007A7A"))
            ).apply { cornerRadius = dpToPx(26).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56))
                .apply { bottomMargin = dpToPx(24) }
        }
        root.addView(startBtn)

        // Music Player Controls
        val playerView = PlayerControlView(this).apply {
            showTimeoutMs = 0 // Keep controls visible always
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(24) }
        }
        playerView.setShowNextButton(false)
        playerView.setShowPreviousButton(false)
        root.addView(playerView)

        // Activity Log
        root.addView(TextView(this).apply {
            text = "📜  AI Intelligence Log"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dpToPx(32); bottomMargin = dpToPx(12) }
        })

        logContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#0D1614"))
            }
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(200))
        }
        val logScroll = ScrollView(this).apply {
            addView(logContainer)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(200))
        }
        root.addView(logScroll)

        addLogEntry("AI System initialized. Ready for deep synchronization.")

        startBtn.setOnClickListener {
            if (!isAutonomous) {
                isAutonomous = true
                startBtn.text = "⏸  Pause AI Autonomy"
                statusTv.text = "System Status: SCANNING"
                addLogEntry("Autonomous mode enabled. Analyzing bio-patterns...")
            } else {
                isAutonomous = false
                startBtn.text = "🚀  Resume AI Autonomy"
                statusTv.text = "System Status: PAUSED"
                audioManager.stopSound()
                addLogEntry("Autonomous mode paused by user.")
            }
        }

        setContentView(rootFrame)

        audioManager = BinauralAudioManager.getInstance(this)
        playerView.player = audioManager.player

        observeBioData()
        observeStress()
    }

    private fun addLogEntry(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = TextView(this).apply {
            text = "[$time] $message"
            textSize = 12f
            setTextColor(Color.parseColor("#80FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(4) }
        }
        logContainer.addView(entry, 0)
    }

    private fun observeBioData() {
        lifecycleScope.launch {
            bluetoothLeManager.bioDataStream.collect { data ->
                stressDetectionEngine.analyze(data)
                if (isAutonomous) {
                    processAiLogic(data)
                }
            }
        }
    }

    private fun observeStress() {
        lifecycleScope.launch {
            stressDetectionEngine.currentStress.collect { state ->
                if (isAutonomous && audioManager.isPlaying.value) {
                    audioManager.updateAdaptiveVolume(state.score)
                }
            }
        }
    }

    private fun processAiLogic(data: BioData) {
        val ratio = data.beta / data.alpha
        val stressScore = stressDetectionEngine.currentStress.value.score

        val (soundType, reason) = when {
            stressScore > 70 -> SoundType.RELAX to "High stress detected ($stressScore). Switching to Alpha waves for calming."
            ratio > 1.8 -> SoundType.FOCUS to "High cognitive effort detected (Beta ratio: ${String.format(Locale.getDefault(), "%.2f", ratio)}). Optimizing for concentration."
            data.theta > 0.45 -> SoundType.MEDITATE to "Deep Theta flow detected. Enhancing meditation depth."
            data.heartRate < 60 && data.beta < 0.2 -> SoundType.SLEEP to "Pre-sleep patterns identified. Switching to Delta induction."
            else -> null to null
        }

        if (soundType != null && soundType != audioManager.currentSoundType) {
            lifecycleScope.launch {
                statusTv.text = "System Status: ADAPTING"
                recommendationTv.text = "Switching to ${soundType.name}..."
                aiReasoningTv.text = reason
                addLogEntry("AI Intelligence: $reason")
                audioManager.playSound(soundType)
                delay(1000)
                statusTv.text = "System Status: ACTIVE"
                recommendationTv.text = "Optimal Mode: ${soundType.name}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.stopSound()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
