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

class OnboardingActivity3 : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create root ConstraintLayout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#121212"))
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
            setTextColor(Color.parseColor("#00E6B8"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0) // Remove padding to center text in fixed width
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48), // Fixed width for balancing
                dpToPx(48)
            )
            setOnClickListener {
                finish()
            }
        }
        headerLayout.addView(backButton)
        
        // App title with branded logo style
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
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
                dpToPx(48), // Match back button width
                dpToPx(48)
            )
            visibility = View.INVISIBLE
        }
        headerLayout.addView(dummyView)
        
        // Gaming chair image
        val chairImage = ImageView(this).apply {
            id = View.generateViewId()
            try {
                setImageResource(R.drawable.cushion)
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#333333"))
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                dpToPx(300)
            )
        }
        rootLayout.addView(chairImage)
        
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
        
        // Create 5 pagination dots (third one is active)
        for (i in 0 until 5) {
            val dot = View(this).apply {
                id = View.generateViewId()
                val dotDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (i == 2) Color.WHITE else Color.parseColor("#00E6B8"))
                    cornerRadius = dpToPx(2).toFloat()
                }
                background = dotDrawable
                layoutParams = LinearLayout.LayoutParams(
                    if (i == 2) dpToPx(24) else dpToPx(16),
                    dpToPx(4)
                ).apply {
                    marginEnd = dpToPx(4)
                }
            }
            paginationLayout.addView(dot)
        }
        
        // Descriptive text
        val descriptionText = TextView(this).apply {
            id = View.generateViewId()
            text = "Stress is silently damaging\nmore lives than ever\nbefore"
            textSize = 18f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#00E6B8"))
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(descriptionText)
        
        // "Proceed to next !" button
        val proceedButton = Button(this).apply {
            id = View.generateViewId()
            text = "Proceed to next !"
            textSize = 16f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#00E6B8"))
            setBackgroundColor(Color.TRANSPARENT)
            
            val buttonDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(2), Color.parseColor("#00E6B8"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = buttonDrawable
            
            setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            
            setOnClickListener {
                val intent = Intent(this@OnboardingActivity3, OnboardingActivity4::class.java)
                startActivity(intent)
            }
        }
        rootLayout.addView(proceedButton)
        
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
        
        // Chair image
        constraintSet.connect(
            chairImage.id,
            ConstraintSet.TOP,
            headerLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.connect(
            chairImage.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            0
        )
        constraintSet.connect(
            chairImage.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            0
        )
        
        // Pagination
        constraintSet.connect(
            paginationLayout.id,
            ConstraintSet.TOP,
            chairImage.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.centerHorizontally(paginationLayout.id, ConstraintSet.PARENT_ID)
        
        // Description
        constraintSet.connect(
            descriptionText.id,
            ConstraintSet.TOP,
            paginationLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(32)
        )
        constraintSet.centerHorizontally(descriptionText.id, ConstraintSet.PARENT_ID)
        
        // Button
        constraintSet.connect(
            proceedButton.id,
            ConstraintSet.TOP,
            descriptionText.id,
            ConstraintSet.BOTTOM,
            dpToPx(40)
        )
        constraintSet.connect(
            proceedButton.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
            dpToPx(40)
        )
        constraintSet.centerHorizontally(proceedButton.id, ConstraintSet.PARENT_ID)
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
