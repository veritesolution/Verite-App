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

class MeditateActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundResource(R.drawable.group_1000006461)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(20), dpToPx(32), dpToPx(20), dpToPx(40))
        }
        scrollView.addView(root)

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
                text = hz; textSize = 12f; setTextColor(Color.parseColor("#AAAFFFFFF"))
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

        // Timer
        val timerTv = TextView(this).apply {
            text = "15:00"
            textSize = 56f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(timerTv)

        root.addView(TextView(this).apply {
            text = "Meditation Session"
            textSize = 13f
            setTextColor(Color.parseColor("#CCAAFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(24) }
        })

        // Start button
        val startBtn = TextView(this).apply {
            text = "▶  Start Meditation"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#1A0A2A"), Color.parseColor("#4A1A6A"))
            ).apply { cornerRadius = dpToPx(26).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(54))
                .apply { bottomMargin = dpToPx(14) }
        }
        root.addView(startBtn)

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

        startBtn.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                startBtn.text = "⏹  Stop Meditation"
                timer = object : CountDownTimer(15 * 60 * 1000L, 1000) {
                    override fun onTick(ms: Long) {
                        val m = (ms / 1000) / 60; val s = (ms / 1000) % 60
                        timerTv.text = String.format("%02d:%02d", m, s)
                    }
                    override fun onFinish() {
                        isRunning = false
                        startBtn.text = "▶  Start Meditation"
                        timerTv.text = "15:00"
                        startActivity(Intent(this@MeditateActivity, BioFeedbackActivity::class.java))
                    }
                }.start()
            } else {
                isRunning = false
                timer?.cancel()
                startBtn.text = "▶  Start Meditation"
                timerTv.text = "15:00"
            }
        }

        setContentView(scrollView)
    }

    override fun onDestroy() { super.onDestroy(); timer?.cancel() }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
