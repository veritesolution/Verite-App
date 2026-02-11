package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

class BackrestControlActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#000000"))
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
            text = "Backrest Controls"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        // Main Controls ScrollView
        val scrollView = ScrollView(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(0, 0)
        }
        rootLayout.addView(scrollView)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(100))
        }
        scrollView.addView(contentLayout)

        // Massage Intensity
        contentLayout.addView(createControlCard("Massage Intensity", "Adjust vibration strength") {
            val seek = SeekBar(this).apply {
                max = 100
                progress = 60
                progressDrawable = ContextCompat.getDrawable(this@BackrestControlActivity, R.drawable.progress_gradient_teal)
                thumb.setColorFilter(Color.parseColor("#00BFA5"), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            it.addView(seek)
        })

        // Massage Patterns
        contentLayout.addView(createControlCard("Massage Mode", "Select vibration pattern") {
            val radioGroup = RadioGroup(this).apply {
                orientation = RadioGroup.HORIZONTAL
                setPadding(0, dpToPx(12), 0, 0)
            }
            val modes = listOf("Gentle", "Pulse", "Deep")
            modes.forEach { mode ->
                val rb = RadioButton(this).apply {
                    text = mode
                    setTextColor(Color.WHITE)
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5"))
                    layoutParams = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                radioGroup.addView(rb)
            }
            it.addView(radioGroup)
        })

        // Heat Therapy
        contentLayout.addView(createControlCard("Heat Therapy", "Soothing warmth for spine") {
            val heatRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(12), 0, 0)
            }
            val statusText = TextView(this).apply {
                text = "Off"
                setTextColor(Color.parseColor("#AAAAAA"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val toggle = Switch(this).apply {
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5"))
                trackTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1B4D4B"))
                setOnCheckedChangeListener { _, isChecked ->
                    statusText.text = if (isChecked) "On (38°C)" else "Off"
                    statusText.setTextColor(if (isChecked) Color.parseColor("#FF5722") else Color.parseColor("#AAAAAA"))
                }
            }
            heatRow.addView(statusText)
            heatRow.addView(toggle)
            it.addView(heatRow)
        })

        // Constraints
        val set = ConstraintSet()
        set.clone(rootLayout)

        set.connect(header.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(scrollView.id, ConstraintSet.TOP, header.id, ConstraintSet.BOTTOM)
        set.connect(scrollView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.connect(scrollView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(scrollView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        set.applyTo(rootLayout)
        setContentView(rootLayout)
    }

    private fun createControlCard(title: String, subtitle: String, addCustomView: (LinearLayout) -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#051212"))
                cornerRadius = dpToPx(24).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dpToPx(16)) }
        }

        val titleTv = TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val subTv = TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
        }

        card.addView(titleTv)
        card.addView(subTv)
        addCustomView(card)
        return card
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
