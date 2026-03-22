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

class OnboardingActivity1 : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure edge-to-edge display
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Create root ConstraintLayout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // 1. Background Video
        val videoView = android.widget.VideoView(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(0, 0)
        }
        rootLayout.addView(videoView, 0)

        val uri = android.net.Uri.parse("android.resource://" + packageName + "/" + R.raw.background_video)
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f) // Mute audio
            
            videoView.post {
                val videoWidth = mp.videoWidth.toFloat()
                val videoHeight = mp.videoHeight.toFloat()
                if (videoWidth == 0f || videoHeight == 0f) return@post
                
                val videoRatio = videoWidth / videoHeight
                val screenRatio = videoView.width.toFloat() / videoView.height.toFloat()
                
                val scale = if (videoRatio >= screenRatio) {
                    videoRatio / screenRatio
                } else {
                    screenRatio / videoRatio
                }
                
                videoView.scaleX = scale
                videoView.scaleY = scale
            }
            mp.start()
        }
        
        // Create header with back button and title
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
        
        // Back arrow button
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
        
        // Sleep mask image
        val sleepMaskImage = ImageView(this).apply {
            id = View.generateViewId()
            // Load from drawable - adjust resource name based on actual asset
            try {
                setImageResource(R.drawable.headband)
            } catch (e: Exception) {
                // Fallback if image not found
                setBackgroundColor(Color.parseColor("#009688"))
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            layoutParams = ConstraintLayout.LayoutParams(
                dpToPx(200),
                dpToPx(200)
            )
        }
        rootLayout.addView(sleepMaskImage)
        
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
        
        // Create 5 pagination dots
        for (i in 0 until 5) {
            val dot = View(this).apply {
                id = View.generateViewId()
                val dotDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (i == 0) Color.WHITE else Color.parseColor("#009688"))
                    cornerRadius = dpToPx(2).toFloat()
                }
                background = dotDrawable
                layoutParams = LinearLayout.LayoutParams(
                    if (i == 0) dpToPx(24) else dpToPx(16),
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
            setTextColor(Color.WHITE)
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
            setTextColor(Color.parseColor("#009688"))
            setBackgroundColor(Color.TRANSPARENT)
            
            val buttonDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(2), Color.parseColor("#009688"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = buttonDrawable
            
            setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            
            setOnClickListener {
                val intent = Intent(this@OnboardingActivity1, OnboardingActivity2::class.java)
                startActivity(intent)
            }
        }
        rootLayout.addView(proceedButton)
        
        // Set constraints
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        
        // Video constraints
        constraintSet.connect(videoView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(videoView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(videoView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(videoView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        
        // Header constraints
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
        
        // Sleep mask image constraints
        constraintSet.connect(
            sleepMaskImage.id,
            ConstraintSet.TOP,
            headerLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(40)
        )
        constraintSet.centerHorizontally(sleepMaskImage.id, ConstraintSet.PARENT_ID)
        
        // Pagination constraints
        constraintSet.connect(
            paginationLayout.id,
            ConstraintSet.TOP,
            sleepMaskImage.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.centerHorizontally(paginationLayout.id, ConstraintSet.PARENT_ID)
        
        // Description text constraints
        constraintSet.connect(
            descriptionText.id,
            ConstraintSet.TOP,
            paginationLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(32)
        )
        constraintSet.centerHorizontally(descriptionText.id, ConstraintSet.PARENT_ID)
        
        // Button constraints
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
