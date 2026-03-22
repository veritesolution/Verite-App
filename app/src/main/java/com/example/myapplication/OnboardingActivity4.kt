package com.example.myapplication

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

class OnboardingActivity4 : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Root Layout with Full-Page Gradient Background
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Dark gradient background (Top to Bottom)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#1A1A1A"), // Top: Dark Grey
                    Color.parseColor("#000000")  // Bottom: Black
                )
            )
        }

        // 2. Header Layout
        val headerLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(headerLayout)

        // Back Button
        val backButton = Button(this).apply {
            id = View.generateViewId()
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48), // Fixed width
                dpToPx(48)
            )
            setOnClickListener { finish() }
        }
        headerLayout.addView(backButton)

        // Title "Vérité" with branded logo style
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(titleText)
        headerLayout.addView(titleText)

        // Dummy balancing view
        val dummyView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48)
            )
            visibility = View.INVISIBLE
        }
        headerLayout.addView(dummyView)

        // 3. Pagination Indicators
        val paginationLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(paginationLayout)

        // 5 dots (index 3 active)
        for (i in 0 until 5) {
            val dot = View(this).apply {
                id = View.generateViewId()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (i == 3) Color.WHITE else Color.parseColor("#00D89A"))
                    cornerRadius = dpToPx(2).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    if (i == 3) dpToPx(24) else dpToPx(16),
                    dpToPx(4)
                ).apply {
                    marginEnd = dpToPx(4)
                }
            }
            paginationLayout.addView(dot)
        }

        // 4. Content Texts
        // "Having issues with Heat /"
        val heatText = TextView(this).apply {
            id = View.generateViewId()
            text = "Having issues with Heat /"
            textSize = 18f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#4CD9AC"))
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(heatText)

        // "cold temperatures"
        val coldText = TextView(this).apply {
            id = View.generateViewId()
            text = "cold temperatures"
            textSize = 18f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#4CD9AC"))
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(coldText)

        // "Visit Temperature Control"
        val visitText = TextView(this).apply {
            id = View.generateViewId()
            text = "\" Visit Temperature Control \""
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(visitText)

        // 5. Stylized "Next" Button
        val nextButton = Button(this).apply {
            id = View.generateViewId()
            text = "→" 
            textSize = 28f
            setTextColor(Color.WHITE) // White text on colored background
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(60), dpToPx(60)) // Circular/Pill
            
            // Linear Gradient Background (Teal to Darker Teal)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#00E6B8"), Color.parseColor("#009688"))
            ).apply {
                shape = GradientDrawable.OVAL // Circular for arrow
                setStroke(0, Color.TRANSPARENT)
            }
            
            // Subtle Box Shadow (Elevation)
            elevation = dpToPx(10).toFloat()
            stateListAnimator = null // Remove default shadows
            
            // Scale Animation (Hover/Press Effect)
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.9f)
                        val scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.9f)
                        scaleDownX.duration = 100
                        scaleDownY.duration = 100
                        val scaleDown = AnimatorSet()
                        scaleDown.play(scaleDownX).with(scaleDownY)
                        scaleDown.start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1f)
                        val scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1f)
                        scaleUpX.duration = 100
                        scaleUpY.duration = 100
                        val scaleUp = AnimatorSet()
                        scaleUp.play(scaleUpX).with(scaleUpY)
                        scaleUp.start()
                    }
                }
                false // dispatch click
            }

            setOnClickListener {
                startActivity(Intent(this@OnboardingActivity4, OnboardingActivity5::class.java))
            }
        }
        rootLayout.addView(nextButton)

        // 6. Main Image (Seat)
        val backgroundImage = ImageView(this).apply {
            id = View.generateViewId()
            try {
                 // Try to use the specific image mentioned by user, fallback to cushion
                 setImageResource(R.drawable.gemini_generated_image_1o0gl61o0gl61o0g_removebg_preview)
            } catch (e: Exception) {
                 try { setImageResource(R.drawable.cushion) } catch (e: Exception) {}
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(backgroundImage)


        // 7. Constraints (Responsive using Guidelines/Chains)
        val set = ConstraintSet()
        set.clone(rootLayout)

        // Header: Top
        set.connect(headerLayout.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(40))
        set.connect(headerLayout.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT)
        set.connect(headerLayout.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT)

        // Pagination: Below Header
        set.connect(paginationLayout.id, ConstraintSet.TOP, headerLayout.id, ConstraintSet.BOTTOM, dpToPx(16))
        set.centerHorizontally(paginationLayout.id, ConstraintSet.PARENT_ID)

        // Heat Text: Below Pagination
        set.connect(heatText.id, ConstraintSet.TOP, paginationLayout.id, ConstraintSet.BOTTOM, dpToPx(40))
        set.centerHorizontally(heatText.id, ConstraintSet.PARENT_ID)

        // Cold Text: Below Heat Text
        set.connect(coldText.id, ConstraintSet.TOP, heatText.id, ConstraintSet.BOTTOM, dpToPx(8))
        set.centerHorizontally(coldText.id, ConstraintSet.PARENT_ID)

        // Visit Text: Below Cold Text
        set.connect(visitText.id, ConstraintSet.TOP, coldText.id, ConstraintSet.BOTTOM, dpToPx(24))
        set.centerHorizontally(visitText.id, ConstraintSet.PARENT_ID)

        // Next Button: Bottom Center
        set.connect(nextButton.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dpToPx(48))
        set.centerHorizontally(nextButton.id, ConstraintSet.PARENT_ID)

        // Image: Between Visit Text and Next Button (Filling remaining space)
        set.connect(backgroundImage.id, ConstraintSet.TOP, visitText.id, ConstraintSet.BOTTOM, dpToPx(16))
        set.connect(backgroundImage.id, ConstraintSet.BOTTOM, nextButton.id, ConstraintSet.TOP, dpToPx(16))
        set.connect(backgroundImage.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(32))
        set.connect(backgroundImage.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(32))
        // Vertical bias to center the image in the available space
        set.setVerticalBias(backgroundImage.id, 0.5f)

        set.applyTo(rootLayout)
        setContentView(rootLayout)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
