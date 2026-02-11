package com.example.myapplication

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

class PostureAnalysisActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#001A1A"))
        }

        // Header
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(40), dpToPx(16), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(header)

        val backButton = TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }
        header.addView(backButton)

        val title = TextView(this).apply {
            text = "Posture Analysis"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        // Spine Illustration Container
        val spineContainer = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(0, dpToPx(400))
        }
        rootLayout.addView(spineContainer)

        // Spine Placeholder (Generic Icon/Shape)
        val spineImg = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_sort_by_size) 
            setColorFilter(Color.parseColor("#00BFA5"))
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(200), dpToPx(350))
        }
        spineContainer.addView(spineImg)

        // "Good Alignment" Badge
        val statusBadge = TextView(this).apply {
            id = View.generateViewId()
            text = "OPTIMAL ALIGNMENT"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2E7D32"))
                cornerRadius = dpToPx(16).toFloat()
            }
        }
        spineContainer.addView(statusBadge)

        // Metrics Card
        val metricsCard = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#051212"))
                cornerRadius = dpToPx(24).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00BFA5"))
            }
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(metricsCard)

        metricsCard.addView(createMetricRow("Lumbar Support", 85))
        metricsCard.addView(createMetricRow("Pressure Balance", 92))
        metricsCard.addView(createMetricRow("Incline Angle", 102)) // 102 degrees

        // Constraints
        val set = ConstraintSet()
        set.clone(rootLayout)

        set.connect(header.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        
        set.connect(spineContainer.id, ConstraintSet.TOP, header.id, ConstraintSet.BOTTOM, dpToPx(20))
        set.centerHorizontally(spineContainer.id, ConstraintSet.PARENT_ID)

        val spineSet = ConstraintSet()
        spineSet.clone(spineContainer)
        spineSet.centerHorizontally(spineImg.id, ConstraintSet.PARENT_ID)
        spineSet.centerVertically(spineImg.id, ConstraintSet.PARENT_ID)
        spineSet.connect(statusBadge.id, ConstraintSet.BOTTOM, spineImg.id, ConstraintSet.TOP, dpToPx(16))
        spineSet.centerHorizontally(statusBadge.id, ConstraintSet.PARENT_ID)
        spineSet.applyTo(spineContainer)

        set.connect(metricsCard.id, ConstraintSet.TOP, spineContainer.id, ConstraintSet.BOTTOM, dpToPx(20))
        set.connect(metricsCard.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, dpToPx(16))
        set.connect(metricsCard.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, dpToPx(16))

        set.applyTo(rootLayout)
        setContentView(rootLayout)
    }

    private fun createMetricRow(label: String, value: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dpToPx(16))
            
            val topRow = LinearLayout(this@PostureAnalysisActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            
            val labelText = TextView(this@PostureAnalysisActivity).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val valueText = TextView(this@PostureAnalysisActivity).apply {
                text = if (label == "Incline Angle") "$value°" else "$value%"
                setTextColor(Color.parseColor("#4DB6AC"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            }
            
            topRow.addView(labelText)
            topRow.addView(valueText)
            addView(topRow)

            val progressBar = ProgressBar(this@PostureAnalysisActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                progress = if (label == "Incline Angle") (value / 180f * 100).toInt() else value
                progressDrawable = ContextCompat.getDrawable(this@PostureAnalysisActivity, R.drawable.progress_gradient_teal)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(4)
                ).apply { topMargin = dpToPx(8) }
            }
            addView(progressBar)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
