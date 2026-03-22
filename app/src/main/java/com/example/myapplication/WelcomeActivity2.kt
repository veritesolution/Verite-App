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
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

class WelcomeActivity2 : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create root ConstraintLayout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#1B4D4B"))
        }
        

            )
        }
        rootLayout.addView(videoView, 0)

        val uri = android.net.Uri.parse("android.resource://" + packageName + "/" + R.raw.background_video)
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f) // Mute audio
            
            // Bypass letterboxing by resizing the VideoView larger than the screen
            videoView.post {
                val videoWidth = mp.videoWidth.toFloat()
                val videoHeight = mp.videoHeight.toFloat()
                if (videoWidth == 0f || videoHeight == 0f) return@post
                
                val videoProportion = videoWidth / videoHeight
                val screenWidth = rootLayout.width.toFloat()
                val screenHeight = rootLayout.height.toFloat()
                val screenProportion = screenWidth / screenHeight
                
                val lp = videoView.layoutParams
                if (videoProportion > screenProportion) {
                    lp.width = (screenHeight * videoProportion).toInt()
                    lp.height = screenHeight.toInt()
                } else {
                    lp.width = screenWidth.toInt()
                    lp.height = (screenWidth / videoProportion).toInt()
                }
                videoView.layoutParams = lp
            }
            mp.start()
        }
        
        // "Welcoming you to" TextView
        val welcomingText = TextView(this).apply {
            id = View.generateViewId()
            text = "Welcoming you\nto"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(welcomingText)
        
        // "Vérité" TextView with branded logo style
        val veriteText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 56f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(veriteText)
        rootLayout.addView(veriteText)
        
        // Tagline TextView with underline
        val taglineText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 16f
            setTypeface(null, Typeface.NORMAL)
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
        
        // Constrain "Welcoming you to" text
        constraintSet.connect(
            welcomingText.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
            dpToPx(120)
        )
        constraintSet.centerHorizontally(welcomingText.id, ConstraintSet.PARENT_ID)
        
        // Constrain "Vérité" text
        constraintSet.connect(
            veriteText.id,
            ConstraintSet.TOP,
            welcomingText.id,
            ConstraintSet.BOTTOM,
            dpToPx(70)
        )
        constraintSet.centerHorizontally(veriteText.id, ConstraintSet.PARENT_ID)
        
        // Constrain tagline
        constraintSet.connect(
            taglineText.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
            dpToPx(120)
        )
        constraintSet.centerHorizontally(taglineText.id, ConstraintSet.PARENT_ID)
        
        // Constrain VideoView to fill parent
        constraintSet.connect(videoView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(videoView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(videoView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(videoView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        
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
