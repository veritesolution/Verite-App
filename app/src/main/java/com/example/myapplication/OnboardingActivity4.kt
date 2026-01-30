package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
        
        // Create root ConstraintLayout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        
        // Header layout
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
        
        // Back button
        val backButton = Button(this).apply {
            id = View.generateViewId()
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                finish()
            }
        }
        headerLayout.addView(backButton)
        
        // App title
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            text = "Vérité"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        headerLayout.addView(titleText)
        
        // Pagination indicators
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
        
        // Create 5 pagination dots (fourth one is active)
        for (i in 0 until 5) {
            val dot = View(this).apply {
                id = View.generateViewId()
                val dotDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (i == 3) Color.WHITE else Color.parseColor("#00D89A"))
                    cornerRadius = dpToPx(2).toFloat()
                }
                background = dotDrawable
                layoutParams = LinearLayout.LayoutParams(
                    if (i == 3) dpToPx(24) else dpToPx(16),
                    dpToPx(4)
                ).apply {
                    marginEnd = dpToPx(4)
                }
            }
            paginationLayout.addView(dot)
        }
        
        // "Having issues with Heat /" text
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
        
        // "cold temperatures" text
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
        
        // "Visit Temperature Control" text with quotes
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
        
        // Circular arrow button
        val nextButton = Button(this).apply {
            id = View.generateViewId()
            text = "→"
            textSize = 24f
            setTextColor(Color.parseColor("#00D89A"))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            
            val size = dpToPx(64)
            layoutParams = ConstraintLayout.LayoutParams(size, size)
            
            setOnClickListener {
                val intent = Intent(this@OnboardingActivity4, OnboardingActivity5::class.java)
                startActivity(intent)
            }
        }
        rootLayout.addView(nextButton)
        
        // Background image (person at computer)
        val backgroundImage = ImageView(this).apply {
            id = View.generateViewId()
            try {
                setImageResource(R.drawable.phone)
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#333333"))
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            alpha = 0.5f
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(backgroundImage)
        
        // Set constraints
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        
        // Background image (behind everything)
        constraintSet.connect(
            backgroundImage.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
            0
        )
        constraintSet.connect(
            backgroundImage.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
            0
        )
        constraintSet.connect(
            backgroundImage.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            0
        )
        constraintSet.connect(
            backgroundImage.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            0
        )
        
        // Header
        constraintSet.connect(
            headerLayout.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
            dpToPx(40)
        )
        constraintSet.connect(
            headerLayout.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            0
        )
        constraintSet.connect(
            headerLayout.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            0
        )
        
        // Pagination
        constraintSet.connect(
            paginationLayout.id,
            ConstraintSet.TOP,
            headerLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(16)
        )
        constraintSet.centerHorizontally(paginationLayout.id, ConstraintSet.PARENT_ID)
        
        // Heat text
        constraintSet.connect(
            heatText.id,
            ConstraintSet.TOP,
            paginationLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(60)
        )
        constraintSet.centerHorizontally(heatText.id, ConstraintSet.PARENT_ID)
        
        // Cold text
        constraintSet.connect(
            coldText.id,
            ConstraintSet.TOP,
            heatText.id,
            ConstraintSet.BOTTOM,
            dpToPx(8)
        )
        constraintSet.centerHorizontally(coldText.id, ConstraintSet.PARENT_ID)
        
        // Visit text
        constraintSet.connect(
            visitText.id,
            ConstraintSet.TOP,
            coldText.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.centerHorizontally(visitText.id, ConstraintSet.PARENT_ID)
        
        // Next button
        constraintSet.connect(
            nextButton.id,
            ConstraintSet.TOP,
            visitText.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.centerHorizontally(nextButton.id, ConstraintSet.PARENT_ID)
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
