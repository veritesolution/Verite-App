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

class OnboardingActivity5 : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Root Layout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // Header Layout
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
                dpToPx(48), // Fixed Width
                dpToPx(48)
            )
            setOnClickListener { finish() }
        }
        headerLayout.addView(backButton)

        // Title
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            
            val spannable = SpannableString("Vérité")
            spannable.setSpan(
                ForegroundColorSpan(Color.WHITE),
                0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00E6B8")), // Standard Teal
                3, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            setTextColor(Color.WHITE)
            text = spannable
            
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
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

        // Image
        val contentImage = ImageView(this).apply {
            id = View.generateViewId()
            try {
                setImageResource(R.drawable.cushion_and_phone_removebg_preview__1_)
            } catch (e: Exception) {
               setBackgroundColor(Color.DKGRAY)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT // Will be constrained
            ).apply {
                 // The constraints will handle height/positioning
            }
        }
        rootLayout.addView(contentImage)

        // Indicators
        val indicatorsLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(indicatorsLayout)

        // 5 dots. 4 teal, 1 white (longer).
        for (i in 0 until 5) {
            val dot = View(this).apply {
                val isLast = i == 4
                val width = if (isLast) 32 else 24
                val color = if (isLast) Color.WHITE else Color.parseColor("#009688")
                
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(color)
                    cornerRadius = dpToPx(2).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(width),
                    dpToPx(4)
                ).apply {
                    marginEnd = if (i < 4) dpToPx(4) else 0
                }
            }
            indicatorsLayout.addView(dot)
        }

        // Description
        val descriptionText = TextView(this).apply {
            id = View.generateViewId()
            text = "Stress is silently damaging\nmore lives than ever\nbefore"
            setTextColor(Color.parseColor("#009688"))
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(descriptionText)

        // Button
        val actionButton = Button(this).apply {
            id = View.generateViewId()
            text = "Sign Up / In"
            setTextColor(Color.parseColor("#009688"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(2), Color.parseColor("#009688"))
                cornerRadius = dpToPx(8).toFloat()
            }
            setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            
            setOnClickListener {
                startActivity(Intent(this@OnboardingActivity5, SignUpActivity::class.java))
            }
        }
        rootLayout.addView(actionButton)

        // Constraints
        val set = ConstraintSet()
        set.clone(rootLayout)

        // Header: Top of parent
        set.connect(headerLayout.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(40))
        set.connect(headerLayout.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT)
        set.connect(headerLayout.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT)

        // Button: Bottom of parent
        set.connect(actionButton.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dpToPx(48))
        set.connect(actionButton.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(32))
        set.connect(actionButton.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(32))

        // Description: Above button
        set.connect(descriptionText.id, ConstraintSet.BOTTOM, actionButton.id, ConstraintSet.TOP, dpToPx(48))
        set.centerHorizontally(descriptionText.id, ConstraintSet.PARENT_ID)

        // Indicators: Above description
        set.connect(indicatorsLayout.id, ConstraintSet.BOTTOM, descriptionText.id, ConstraintSet.TOP, dpToPx(32))
        set.centerHorizontally(indicatorsLayout.id, ConstraintSet.PARENT_ID)

        // Image: Between Header and Indicators
        set.connect(contentImage.id, ConstraintSet.TOP, headerLayout.id, ConstraintSet.BOTTOM, dpToPx(32))
        set.connect(contentImage.id, ConstraintSet.BOTTOM, indicatorsLayout.id, ConstraintSet.TOP, dpToPx(0))
        set.connect(contentImage.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(32))
        set.connect(contentImage.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(32))
        // Vertical bias to center better if space allows
        set.setVerticalBias(contentImage.id, 0.5f)

        set.applyTo(rootLayout)
        setContentView(rootLayout)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
