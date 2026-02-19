package com.example.myapplication

import android.animation.ValueAnimator
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

class SleepSoundActivity : AppCompatActivity() {

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
            text = "🌙  Sleep Mode"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dpToPx(12), 0, dpToPx(4)) }
        })

        root.addView(TextView(this).apply {
            text = "Binaural Beats: Delta (0.5–4 Hz) + Gentle Vibration"
            textSize = 13f
            setTextColor(Color.parseColor("#5A3A8A"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(24) }
        })

        // Moon/stars visual
        val moonView = TextView(this).apply {
            text = "🌙\n✨  ⭐  ✨"
            textSize = 36f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(120)).apply {
                bottomMargin = dpToPx(16)
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(20).toFloat()
                colors = intArrayOf(Color.parseColor("#0D0A2E"), Color.parseColor("#1A0A3A"))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
        }
        root.addView(moonView)

        // Subtle fade
        ValueAnimator.ofFloat(0.6f, 1f, 0.6f).apply {
            duration = 3000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { moonView.alpha = it.animatedValue as Float }
            start()
        }

        val timerTv = TextView(this).apply {
            text = "30:00"
            textSize = 56f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(timerTv)

        root.addView(TextView(this).apply {
            text = "Sleep Induction Session"
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(24) }
        })

        // Vibration row
        val vibRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(20) }
        }
        vibRow.addView(TextView(this).apply {
            text = "📳 Gentle Vibration:"
            textSize = 14f
            setTextColor(Color.parseColor("#CCFFFFFF"))
        })
        vibRow.addView(Switch(this).apply {
            isChecked = true
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7A4AA0"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { leftMargin = dpToPx(8) }
        })
        root.addView(vibRow)

        // Start button
        val startBtn = TextView(this).apply {
            text = "▶  Start Sleep Session"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#0D0A2E"), Color.parseColor("#2A1A6E"))
            ).apply { cornerRadius = dpToPx(26).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(54))
                .apply { bottomMargin = dpToPx(16) }
        }
        root.addView(startBtn)

        startBtn.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                startBtn.text = "⏹  Stop Session"
                timer = object : CountDownTimer(30 * 60 * 1000L, 1000) {
                    override fun onTick(ms: Long) {
                        val m = (ms / 1000) / 60; val s = (ms / 1000) % 60
                        timerTv.text = String.format("%02d:%02d", m, s)
                    }
                    override fun onFinish() {
                        isRunning = false
                        startBtn.text = "▶  Start Sleep Session"
                        timerTv.text = "30:00"
                        Toast.makeText(this@SleepSoundActivity, "Sweet dreams! Sleep session complete. 🌙", Toast.LENGTH_LONG).show()
                    }
                }.start()
            } else {
                isRunning = false
                timer?.cancel()
                startBtn.text = "▶  Start Sleep Session"
                timerTv.text = "30:00"
            }
        }

        // Tips card
        val tipCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(14).toFloat()
                setColor(Color.parseColor("#0D0A2E"))
            }
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val tipTitle = TextView(this).apply {
            text = "🌙 Sleep Tips"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#9A70CC"))
        }
        val tips = listOf(
            "• Wear the sleep band before lying down",
            "• Dim your room & lower the temperature",
            "• Avoid screens 30 min before bed",
            "• Vibrations gently guide you to sleep"
        )
        tipCard.addView(tipTitle)
        for (tip in tips) {
            tipCard.addView(TextView(this).apply {
                text = tip; textSize = 12f; setTextColor(Color.parseColor("#CCFFFFFF"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dpToPx(4) }
            })
        }
        root.addView(tipCard)

        setContentView(scrollView)
    }

    override fun onDestroy() { super.onDestroy(); timer?.cancel() }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
