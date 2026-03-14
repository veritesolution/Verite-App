package com.example.myapplication

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class FocusReportActivity : AppCompatActivity() {

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
            text = "Focus Report"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dpToPx(10), 0, dpToPx(4)) }
        })

        root.addView(TextView(this).apply {
            text = "Focused Hours"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dpToPx(20), 0, dpToPx(8)) }
        })

        // Bar Chart View
        val barData = listOf(
            Pair("Mon", 0.4f), Pair("Tue", 0.6f), Pair("Wed", 0.85f),
            Pair("Thu", 0.5f), Pair("Fri", 0.75f), Pair("Sat", 0.9f), Pair("Sun", 0.65f)
        )
        val barChart = object : View(this) {
            private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00BFA5") }
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A3E3E") }
            private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER
            }
            private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#AAFFFF"); textSize = 24f; textAlign = Paint.Align.CENTER
            }
            override fun onDraw(canvas: Canvas) {
                val barW = width / (barData.size * 1.5f + 0.5f)
                val gap = barW * 0.5f
                val chartH = height - 60f
                for (i in barData.indices) {
                    val x = gap + i * (barW + gap)
                    val top = chartH - (barData[i].second * chartH)
                    canvas.drawRoundRect(x, top, x + barW, chartH, 12f, 12f, bgPaint)
                    canvas.drawRoundRect(x, top, x + barW, chartH, 12f, 12f, barPaint)
                    canvas.drawText(barData[i].first, x + barW / 2, height - 10f, labelPaint)
                    val hours = (barData[i].second * 4).toInt()
                    canvas.drawText("${hours}h", x + barW / 2, top - 8f, valPaint)
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(180))
                .apply { bottomMargin = dpToPx(20) }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#0A1F1F"))
            }
        }
        root.addView(barChart)

        // Stats
        val statsData = listOf(
            Triple("Sessions This Week", "6", "📅"),
            Triple("Total Focus Time", "18h 25m", "⏱"),
            Triple("Avg Session Score", "82%", "🎯"),
            Triple("Streak", "5 Days 🔥", "🌟")
        )
        for ((label, value, icon) in statsData) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(14).toFloat()
                    setColor(Color.parseColor("#0A2020"))
                }
                setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dpToPx(10) }
            }
            row.addView(TextView(this).apply { text = icon; textSize = 22f })
            row.addView(TextView(this).apply {
                text = label; textSize = 14f; setTextColor(Color.parseColor("#CCFFFFFF"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { leftMargin = dpToPx(12) }
            })
            row.addView(TextView(this).apply {
                text = value; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#00BFA5"))
            })
            root.addView(row)
        }

        val noteCard = TextView(this).apply {
            text = "📢  When device is not in use, the device will make an attempt to automatically shut down."
            textSize = 12f
            setTextColor(Color.parseColor("#AAFFFFFF"))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#0D1A1A"))
            }
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dpToPx(12) }
        }
        root.addView(noteCard)

        setContentView(scrollView)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
