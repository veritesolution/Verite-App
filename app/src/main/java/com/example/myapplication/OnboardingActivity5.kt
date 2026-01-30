package com.example.myapplication
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
        
        // Create root ConstraintLayout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0A1414"))
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
        
        // Phone mockup container (simplified representation)
        val phoneContainer = ConstraintLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = ConstraintLayout.LayoutParams(
                dpToPx(300),
                dpToPx(600)
            )
        }
        rootLayout.addView(phoneContainer)
        
        // Car seat image inside phone
        val seatImageSmall = ImageView(this).apply {
            id = View.generateViewId()
            try {
                setImageResource(R.drawable.cushion)
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#333333"))
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                dpToPx(200)
            )
        }
        phoneContainer.addView(seatImageSmall)
        
        // Large car seat image
        val seatImageLarge = ImageView(this).apply {
            id = View.generateViewId()
            try {
                setImageResource(R.drawable.cushion)
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#333333"))
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            layoutParams = ConstraintLayout.LayoutParams(
                dpToPx(400),
                dpToPx(400)
            )
        }
        rootLayout.addView(seatImageLarge)
        
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
        
        // Create 5 pagination dots (fifth one is active)
        for (i in 0 until 5) {
            val dot = View(this).apply {
                id = View.generateViewId()
                val dotDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (i == 4) Color.WHITE else Color.parseColor("#87D6D6"))
                    cornerRadius = dpToPx(2).toFloat()
                }
                background = dotDrawable
                layoutParams = LinearLayout.LayoutParams(
                    if (i == 4) dpToPx(24) else dpToPx(16),
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
            setTextColor(Color.parseColor("#87D6D6"))
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(descriptionText)
        
        // "Sign Up / In" button
        val signUpInButton = Button(this).apply {
            id = View.generateViewId()
            text = "Sign Up / In"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            
            val buttonDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(2), Color.parseColor("#0F3D3D"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = buttonDrawable
            
            setPadding(dpToPx(48), dpToPx(16), dpToPx(48), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(32), 0, dpToPx(32), 0)
            }
            
            setOnClickListener {
                val intent = Intent(this@OnboardingActivity5, SignUpActivity::class.java)
                startActivity(intent)
            }
        }
        rootLayout.addView(signUpInButton)
        
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
        
        // Phone container
        constraintSet.connect(
            phoneContainer.id,
            ConstraintSet.TOP,
            headerLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.centerHorizontally(phoneContainer.id, ConstraintSet.PARENT_ID)
        
        // Seat image inside phone
        val phoneConstraintSet = ConstraintSet()
        phoneConstraintSet.clone(phoneContainer)
        phoneConstraintSet.connect(
            seatImageSmall.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
            dpToPx(100)
        )
        phoneConstraintSet.connect(
            seatImageSmall.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            dpToPx(20)
        )
        phoneConstraintSet.connect(
            seatImageSmall.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            dpToPx(20)
        )
        phoneConstraintSet.applyTo(phoneContainer)
        
        // Large seat image (behind phone)
        constraintSet.connect(
            seatImageLarge.id,
            ConstraintSet.TOP,
            phoneContainer.id,
            ConstraintSet.TOP,
            dpToPx(-50)
        )
        constraintSet.connect(
            seatImageLarge.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            dpToPx(-50)
        )
        
        // Pagination
        constraintSet.connect(
            paginationLayout.id,
            ConstraintSet.TOP,
            phoneContainer.id,
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
            dpToPx(24)
        )
        constraintSet.centerHorizontally(descriptionText.id, ConstraintSet.PARENT_ID)
        
        // Button
        constraintSet.connect(
            signUpInButton.id,
            ConstraintSet.TOP,
            descriptionText.id,
            ConstraintSet.BOTTOM,
            dpToPx(32)
        )
        constraintSet.connect(
            signUpInButton.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
            dpToPx(40)
        )
        constraintSet.connect(
            signUpInButton.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            dpToPx(32)
        )
        constraintSet.connect(
            signUpInButton.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            dpToPx(32)
        )
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
