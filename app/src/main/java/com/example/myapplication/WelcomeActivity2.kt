package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

class WelcomeActivity2 : AppCompatActivity() {
    
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
            setBackgroundColor(Color.BLACK)
        }
        

        }
        rootLayout.addView(videoView, 0)

        val uri = android.net.Uri.parse("android.resource://" + packageName + "/" + R.raw.background_video)
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f) // Mute audio
            
            // Bypass letterboxing by zooming the VideoView symmetrically
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
        
        // 2. Glass Box
        val glassBox = View(this).apply {
            id = View.generateViewId()
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(31).toFloat()
                setColor(Color.argb(89, 47, 73, 73)) // rgba(47, 73, 73, 0.35)
            }
            layoutParams = ConstraintLayout.LayoutParams(0, 0)
        }
        rootLayout.addView(glassBox)

        // 3. "Welcoming you to" TextView
        val welcomingText = TextView(this).apply {
            id = View.generateViewId()
            text = "Welcoming you\nto"
            textSize = 32f
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(welcomingText)
        
        // 4. "Vérité" TextView
        val veriteText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 48f
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
            gravity = Gravity.CENTER
            
            val text = "Vérité"
            val spannable = SpannableString(text)
            val tealColor = Color.parseColor("#1C9C91")
            
            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(tealColor), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 2, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(tealColor), 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            setText(spannable)
            
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(veriteText)
        
        // 5. Tagline TextView with underline
        val taglineText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 16f
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            
            val tagline = "Innovating care for a better\ntomorrow"
            val spannable = SpannableString(tagline)
            spannable.setSpan(UnderlineSpan(), 0, tagline.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            setText(spannable)
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(taglineText)
        
        // Set constraints
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        
        // Constrain VideoView to fill parent
        constraintSet.connect(videoView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(videoView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(videoView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(videoView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        
        // Constrain Glass Box (with margins mimicking left: 35px, top: 69px)
        val horizontalMargin = dpToPx(24)
        val topMargin = dpToPx(48)
        val bottomMargin = dpToPx(48)
        constraintSet.connect(glassBox.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, topMargin)
        constraintSet.connect(glassBox.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, bottomMargin)
        constraintSet.connect(glassBox.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, horizontalMargin)
        constraintSet.connect(glassBox.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, horizontalMargin)

        // Constrain "Welcoming you to" text (top: 125px relative to screen)
        constraintSet.connect(
            welcomingText.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
            dpToPx(90)
        )
        constraintSet.centerHorizontally(welcomingText.id, ConstraintSet.PARENT_ID)
        
        // Constrain "Vérité" text (centered roughly inside the glass box)
        constraintSet.connect(
            veriteText.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP
        )
        constraintSet.connect(
            veriteText.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM
        )
        constraintSet.centerHorizontally(veriteText.id, ConstraintSet.PARENT_ID)
        
        // Constrain tagline (bottom inside the glass box)
        constraintSet.connect(
            taglineText.id,
            ConstraintSet.BOTTOM,
            glassBox.id,
            ConstraintSet.BOTTOM,
            dpToPx(40)
        )
        constraintSet.centerHorizontally(taglineText.id, ConstraintSet.PARENT_ID)
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
        
        // Auto-navigate to next screen after a delay
        rootLayout.postDelayed({
            val intent = Intent(this, OnboardingActivity1::class.java)
            startActivity(intent)
        }, 2000)
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
