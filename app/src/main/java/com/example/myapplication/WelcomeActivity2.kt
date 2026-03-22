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
        
        // Add a VideoView to play background_video.mp4 as background
        val videoView = android.widget.VideoView(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(videoView, 0)

        val uri = android.net.Uri.parse("android.resource://" + packageName + "/" + R.raw.background_video)
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
        }
        videoView.start()
        
        // "Welcoming you to" TextView
        val welcomingText = TextView(this).apply {
            id = View.generateViewId()
            text = "Welcoming you to"
            textSize = 18f
            setTypeface(null, Typeface.NORMAL)
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
            textSize = 48f
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
            dpToPx(200)
        )
        constraintSet.centerHorizontally(welcomingText.id, ConstraintSet.PARENT_ID)
        
        // Constrain "Vérité" text
        constraintSet.connect(
            veriteText.id,
            ConstraintSet.TOP,
            welcomingText.id,
            ConstraintSet.BOTTOM,
            dpToPx(40)
        )
        constraintSet.centerHorizontally(veriteText.id, ConstraintSet.PARENT_ID)
        
        // Constrain tagline
        constraintSet.connect(
            taglineText.id,
            ConstraintSet.TOP,
            veriteText.id,
            ConstraintSet.BOTTOM,
            dpToPx(100)
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
