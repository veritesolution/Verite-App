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

class FocusSoundActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null
    private var isRunning = false
    private val sessionSeconds = 25 * 60L // 25-minute pomodoro session

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

        // Back button
        val backBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setOnClickListener { finish() }
        }
        root.addView(backBtn)

        // Title
        val title = TextView(this).apply {
            text = "🎯  Focus Mode"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dpToPx(12), 0, dpToPx(4)) }
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Binaural Beats: Beta (14–30 Hz)"
            textSize = 13f
            setTextColor(Color.parseColor("#00BFA5"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(24) }
        }
        root.addView(subtitle)

        // Animated waveform visual
        val waveContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(100)).apply {
                bottomMargin = dpToPx(20)
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#0D1F2D"))
            }
        }

        val waveLabel = TextView(this).apply {
            text = "〰〰〰〰〰 Beta Wave 〰〰〰〰〰"
            textSize = 16f
            setTextColor(Color.parseColor("#00BFA5"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        waveContainer.addView(waveLabel)

        // Animate waveform
        ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { waveLabel.scaleY = it.animatedValue as Float }
            start()
        }
        root.addView(waveContainer)

        // Timer display
        val timerTv = TextView(this).apply {
            text = "25:00"
            textSize = 60f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(timerTv)

        val timerLabel = TextView(this).apply {
            text = "Session Time"
            textSize = 13f
            setTextColor(Color.parseColor("#AAFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(28) }
        }
        root.addView(timerLabel)

        // Start/Stop button
        val startBtn = TextView(this).apply {
            text = "▶  Start Focus Session"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#004D5A"), Color.parseColor("#007A7A"))
            ).apply { cornerRadius = dpToPx(26).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(54))
                .apply { bottomMargin = dpToPx(16) }
        }
        root.addView(startBtn)

        // View Report button
        val reportBtn = TextView(this).apply {
            text = "📊  View Focus Report"
            textSize = 15f
            setTextColor(Color.parseColor("#00BFA5"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(26).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00BFA5"))
                setColor(Color.parseColor("#0D1F1F"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52))
            setOnClickListener {
                startActivity(Intent(this@FocusSoundActivity, FocusReportActivity::class.java))
            }
        }
        root.addView(reportBtn)

        // Tip card
        val tipCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(14).toFloat()
                setColor(Color.parseColor("#0A2020"))
            }
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dpToPx(20) }
        }
        val tipTitle = TextView(this).apply {
            text = "💡 Tips for Focus"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00BFA5"))
        }
        val tips = listOf(
            "• Wear the sleep band snugly",
            "• Sit upright in a quiet space",
            "• Avoid distractions for 25 min",
            "• Session results auto-save after completion"
        )
        tipCard.addView(tipTitle)
        for (tip in tips) {
            tipCard.addView(TextView(this).apply {
                text = tip
                textSize = 12f
                setTextColor(Color.parseColor("#CCFFFFFF"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dpToPx(4) }
            })
        }
        root.addView(tipCard)

        // Button Logic
        startBtn.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                startBtn.text = "⏹  Stop Session"
                timer = object : CountDownTimer(sessionSeconds * 1000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val mins = (millisUntilFinished / 1000) / 60
                        val secs = (millisUntilFinished / 1000) % 60
                        timerTv.text = String.format("%02d:%02d", mins, secs)
                    }
                    override fun onFinish() {
                        isRunning = false
                        startBtn.text = "▶  Start Focus Session"
                        timerTv.text = "25:00"
                        startActivity(Intent(this@FocusSoundActivity, FocusReportActivity::class.java))
                    }
                }.start()
            } else {
                isRunning = false
                timer?.cancel()
                startBtn.text = "▶  Start Focus Session"
                timerTv.text = "25:00"
            }
        }

        setContentView(scrollView)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
