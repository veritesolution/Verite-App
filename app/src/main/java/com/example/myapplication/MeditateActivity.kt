package com.example.myapplication

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
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
import com.example.myapplication.data.bluetooth.BluetoothLeManager
import com.example.myapplication.data.logic.StressDetectionEngine
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import kotlinx.coroutines.launch

class MeditateActivity : AppCompatActivity() {

    private var audioManager: BinauralAudioManager? = null
    private var stressDetectionEngine: StressDetectionEngine? = null
    private var bluetoothLeManager: BluetoothLeManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---------- Root FrameLayout to host Background + Content ----------
        val rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ---------- Compose Background ----------
        val composeBackground = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setContent {
                VeriteTheme {
                    SkyBackground { }
                }
            }
        }
        rootFrame.addView(composeBackground)

        // ---------- Content ScrollView ----------
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(20), dpToPx(32), dpToPx(20), dpToPx(40))
        }
        scrollView.addView(root)
        rootFrame.addView(scrollView)

        root.addView(ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setOnClickListener { finish() }
        })

        root.addView(TextView(this).apply {
            text = "🧘  Meditate Mode"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dpToPx(12), 0, dpToPx(4)) }
        })

        val soundscapeTitle = intent.getStringExtra("SOUNDSCAPE_TITLE") ?: "sunset"
        root.addView(TextView(this).apply {
            text = "Selected: $soundscapeTitle"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(8) }
        })

        root.addView(TextView(this).apply {
            text = "Binaural Beats: Theta (4–8 Hz) + Brain Biofeedback"
            textSize = 13f
            setTextColor(Color.parseColor("#7040AA"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(20) }
        })

        // Brain wave monitor card
        val brainCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(18).toFloat()
                setColor(Color.parseColor("#1A0A2A"))
            }
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(20) }
        }

        brainCard.addView(TextView(this).apply {
            text = "🧠 Live Brain Activity Monitor"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#BB80FF"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(12) }
        })

        // Brain wave indicators
        val waves = listOf(
            Triple("Theta", "6.2 Hz", 0.72f),
            Triple("Alpha", "9.8 Hz", 0.45f),
            Triple("Beta", "18.4 Hz", 0.28f),
            Triple("Delta", "1.5 Hz", 0.15f)
        )

        for ((name, hz, progress) in waves) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dpToPx(8) }
            }
            val nameTv = TextView(this).apply {
                text = name; textSize = 13f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dpToPx(50), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; this.progress = (progress * 100).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#BB80FF"))
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(8), 1f).apply {
                    leftMargin = dpToPx(8); rightMargin = dpToPx(8)
                }
            }
            val hzTv = TextView(this).apply {
                text = hz; textSize = 12f; setTextColor(Color.parseColor("#AAFFFFFF"))
                layoutParams = LinearLayout.LayoutParams(dpToPx(58), LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.END
            }

            // Animate the bars
            ValueAnimator.ofInt((progress * 100 * 0.8f).toInt(), (progress * 100).toInt()).apply {
                duration = 2000; repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { bar.progress = it.animatedValue as Int }
                start()
            }

            row.addView(nameTv); row.addView(bar); row.addView(hzTv)
            brainCard.addView(row)
        }

        // Meditation state badge
        val stateBadge = TextView(this).apply {
            text = "State: Deep Theta — Excellent Meditation 🟣"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#BB80FF"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#2A0A4A"))
            }
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dpToPx(12) }
        }
        brainCard.addView(stateBadge)
        root.addView(brainCard)

        // BioFeedback report button
        val reportBtn = TextView(this).apply {
            text = "🧬  View Biofeedback Analysis"
            textSize = 15f
            setTextColor(Color.parseColor("#BB80FF"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(26).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#BB80FF"))
                setColor(Color.parseColor("#1A0A2A"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52))
            setOnClickListener {
                startActivity(Intent(this@MeditateActivity, BioFeedbackActivity::class.java))
            }
        }
        root.addView(reportBtn)

        // Music Player Controls
        val playerView = PlayerControlView(this).apply {
            showTimeoutMs = 0 // Keep controls visible always
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(32); topMargin = dpToPx(20) }
        }
        playerView.setShowNextButton(false)
        playerView.setShowPreviousButton(false)
        root.addView(playerView)

        setContentView(rootFrame)

        try {
            audioManager = BinauralAudioManager.getInstance(this)
            playerView.player = audioManager?.player
            audioManager?.playSound(SoundType.MEDITATE)
        } catch (e: Exception) {
            android.util.Log.e("Meditate", "Audio init failed: ${e.message}")
        }

        try {
            stressDetectionEngine = StressDetectionEngine()
            bluetoothLeManager = BluetoothLeManager.getInstance(this)
            observeBioData()
            observeStress()
        } catch (e: Exception) {
            android.util.Log.e("Meditate", "Bio monitoring init failed: ${e.message}")
        }
    }

    private fun observeBioData() {
        lifecycleScope.launch {
            try {
                bluetoothLeManager?.bioDataStream?.collect { data ->
                    stressDetectionEngine?.analyze(data)
                }
            } catch (e: Exception) {
                android.util.Log.e("Meditate", "Bio data error: ${e.message}")
            }
        }
    }

    private fun observeStress() {
        lifecycleScope.launch {
            try {
                stressDetectionEngine?.currentStress?.collect { state ->
                    if (audioManager?.player?.isPlaying == true) {
                        audioManager?.updateAdaptiveVolume(state.score)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Meditate", "Stress monitoring error: ${e.message}")
            }
        }
    }

    override fun onDestroy() { 
        super.onDestroy()
        try { audioManager?.stopSound() } catch (_: Exception) {}
    }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
