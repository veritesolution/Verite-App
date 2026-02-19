package com.example.myapplication

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class RelaxReportActivity : AppCompatActivity() {

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
            text = "Stress Report"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dpToPx(10), 0, dpToPx(6)) }
        })

        root.addView(TextView(this).apply {
            text = "Before vs After Relax Session"
            textSize = 14f
            setTextColor(Color.parseColor("#AAFFFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(24) }
        })

        // Before/After comparison cards
        val comparison = listOf(
            Triple("Heart Rate", "88 bpm", "72 bpm"),
            Triple("Stress Level", "High (78%)", "Low (22%)"),
            Triple("Cortisol (est.)", "Elevated", "Reduced"),
            Triple("HRV Score", "32 ms", "58 ms")
        )

        for ((metric, before, after) in comparison) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(14).toFloat()
                    setColor(Color.parseColor("#0A1F0A"))
                }
                setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dpToPx(12) }
            }
            card.addView(TextView(this).apply {
                text = metric
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#00BFA5"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dpToPx(8) }
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(buildBadge("BEFORE", before, "#7A2A1A"))
            row.addView(TextView(this).apply {
                text = "  →  "
                textSize = 18f
                setTextColor(Color.parseColor("#AAFFFF"))
            })
            row.addView(buildBadge("AFTER", after, "#1A6B40"))
            card.addView(row)
            root.addView(card)
        }

        root.addView(TextView(this).apply {
            text = "✅ Great session! Your stress reduced significantly. Try to relax daily."
            textSize = 13f
            setTextColor(Color.parseColor("#AAFFFFFF"))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#0D1A0D"))
            }
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dpToPx(8) }
        })

        setContentView(scrollView)
    }

    private fun buildBadge(tag: String, value: String, color: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(10).toFloat()
                setColor(Color.parseColor(color))
            }
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            addView(TextView(this@RelaxReportActivity).apply {
                text = tag
                textSize = 10f
                setTextColor(Color.parseColor("#CCFFFFFF"))
                gravity = Gravity.CENTER
            })
            addView(TextView(this@RelaxReportActivity).apply {
                text = value
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
