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

class OnboardingActivity2 : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create root ConstraintLayout with gradient background
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#001A1A"),
                Color.BLACK
            )
        )
        rootLayout.background = gradientDrawable
        
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
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48)
            )
            setOnClickListener {
                finish()
            }
        }
        headerLayout.addView(backButton)
        
        // App title "Vérité" with branded logo style
        val titleText = TextView(this).apply {
            id = View.generateViewId()
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
        
        // Create 5 pagination dots (second one is active)
        for (i in 0 until 5) {
            val dot = View(this).apply {
                id = View.generateViewId()
                val dotDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (i == 1) Color.WHITE else Color.parseColor("#66CCCC"))
                    cornerRadius = dpToPx(2).toFloat()
                }
                background = dotDrawable
                layoutParams = LinearLayout.LayoutParams(
                    if (i == 1) dpToPx(24) else dpToPx(16),
                    dpToPx(4)
                ).apply {
                    marginEnd = dpToPx(4)
                }
            }
            paginationLayout.addView(dot)
        }
        
        // "Meet Our Psychologist" text
        val meetText = TextView(this).apply {
            id = View.generateViewId()
            text = "Meet Our Psychologist"
            textSize = 18f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#66CCCC"))
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(meetText)
        
        // "Trained AI System" text
        val trainedText = TextView(this).apply {
            id = View.generateViewId()
            text = "Trained AI System"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#66CCCC"))
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(trainedText)
        
        // "Oryn" text with quotes
        val orynText = TextView(this).apply {
            id = View.generateViewId()
            text = "\"Oryn\""
            textSize = 36f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(orynText)
        
        // Circular next button
        val nextButton = Button(this).apply {
            id = View.generateViewId()
            text = "→"
            textSize = 24f
            setTextColor(Color.parseColor("#66CCCC"))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            
            val size = dpToPx(64)
            layoutParams = ConstraintLayout.LayoutParams(size, size)
            
            setOnClickListener {
                val intent = Intent(this@OnboardingActivity2, OnboardingActivity3::class.java)
                startActivity(intent)
            }
        }
        rootLayout.addView(nextButton)
        
        // BCI head image
        val bciImage = ImageView(this).apply {
            id = View.generateViewId()
            try {
                setImageResource(R.drawable.eeg_measure_removebg_preview)
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#009688"))
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                dpToPx(400)
            )
        }
        rootLayout.addView(bciImage)
        
        // Set constraints
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        
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
        
        // Meet text
        constraintSet.connect(
            meetText.id,
            ConstraintSet.TOP,
            paginationLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.centerHorizontally(meetText.id, ConstraintSet.PARENT_ID)
        
        // Trained text
        constraintSet.connect(
            trainedText.id,
            ConstraintSet.TOP,
            meetText.id,
            ConstraintSet.BOTTOM,
            dpToPx(8)
        )
        constraintSet.centerHorizontally(trainedText.id, ConstraintSet.PARENT_ID)
        
        // Oryn text
        constraintSet.connect(
            orynText.id,
            ConstraintSet.TOP,
            trainedText.id,
            ConstraintSet.BOTTOM,
            dpToPx(16)
        )
        constraintSet.centerHorizontally(orynText.id, ConstraintSet.PARENT_ID)
        
        // Next button
        constraintSet.connect(
            nextButton.id,
            ConstraintSet.TOP,
            orynText.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.centerHorizontally(nextButton.id, ConstraintSet.PARENT_ID)
        
        // BCI image
        constraintSet.connect(
            bciImage.id,
            ConstraintSet.TOP,
            nextButton.id,
            ConstraintSet.BOTTOM,
            dpToPx(16)
        )
        constraintSet.connect(
            bciImage.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
            0
        )
        constraintSet.connect(
            bciImage.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            0
        )
        constraintSet.connect(
            bciImage.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            0
        )
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
