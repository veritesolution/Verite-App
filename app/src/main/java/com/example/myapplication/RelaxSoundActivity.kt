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

class RelaxSoundActivity : AppCompatActivity() {

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

        val backBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setOnClickListener { finish() }
        }
        root.addView(backBtn)

        root.addView(TextView(this).apply {
            text = "🌊  Relax Mode"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dpToPx(12), 0, dpToPx(4)) }
        })

        root.addView(TextView(this).apply {
            text = "Binaural Beats: Alpha (8–12 Hz) + Gentle Vibration"
            textSize = 13f
            setTextColor(Color.parseColor("#1A6B40"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(20) }
        })

        // Breathing circle
        val breatheCircle = TextView(this).apply {
            text = "Breathe In…"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.parseColor("#0A2A1A"), Color.parseColor("#1A6B40"))
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dpToPx(80).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(180), dpToPx(180)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(20)
            }
        }
        val breatheWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(breatheCircle)
        }
        root.addView(breatheWrap)

        // Breathing animation
        ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val scale = anim.animatedValue as Float
                breatheCircle.scaleX = scale
                breatheCircle.scaleY = scale
                val progress = anim.animatedFraction
                breatheCircle.text = if (progress < 0.5f) "Breathe In…" else "Breathe Out…"
            }
            start()
        }

        // Timer
        val timerTv = TextView(this).apply {
            text = "20:00"
            textSize = 56f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(timerTv)

        root.addView(TextView(this).apply {
            text = "Relax Session"
            textSize = 13f
            setTextColor(Color.parseColor("#AAFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(28) }
        })

        // Vibration indicator
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
        val vibSwitch = Switch(this).apply {
            isChecked = true
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { leftMargin = dpToPx(8) }
        }
        vibRow.addView(vibSwitch)
        root.addView(vibRow)

        // Start button
        val startBtn = TextView(this).apply {
            text = "▶  Start Relax Session"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#0A2A1A"), Color.parseColor("#1A6B40"))
            ).apply { cornerRadius = dpToPx(26).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(54))
                .apply { bottomMargin = dpToPx(14) }
        }
        root.addView(startBtn)

        // Stress Report button
        val reportBtn = TextView(this).apply {
            text = "📊  View Stress Report"
            textSize = 15f
            setTextColor(Color.parseColor("#1A6B40"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(26).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#1A6B40"))
                setColor(Color.parseColor("#0A1F0A"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52))
            setOnClickListener {
                startActivity(Intent(this@RelaxSoundActivity, RelaxReportActivity::class.java))
            }
        }
        root.addView(reportBtn)

        startBtn.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                startBtn.text = "⏹  Stop Session"
                timer = object : CountDownTimer(20 * 60 * 1000L, 1000) {
                    override fun onTick(ms: Long) {
                        val m = (ms / 1000) / 60; val s = (ms / 1000) % 60
                        timerTv.text = String.format("%02d:%02d", m, s)
                    }
                    override fun onFinish() {
                        isRunning = false
                        startBtn.text = "▶  Start Relax Session"
                        timerTv.text = "20:00"
                        startActivity(Intent(this@RelaxSoundActivity, RelaxReportActivity::class.java))
                    }
                }.start()
            } else {
                isRunning = false
                timer?.cancel()
                startBtn.text = "▶  Start Relax Session"
                timerTv.text = "20:00"
            }
        }

        setContentView(scrollView)
    }

    override fun onDestroy() { super.onDestroy(); timer?.cancel() }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
