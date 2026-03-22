package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.auth.AuthManager
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.DeviceRepository
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {
    
    private lateinit var authManager: AuthManager
    private lateinit var deviceRepository: DeviceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authManager = AuthManager(this)
        val database = AppDatabase.getDatabase(this)
        deviceRepository = DeviceRepository(database.deviceDao())

        // Check for existing session
        if (authManager.currentUser != null) {
            lifecycleScope.launch {
                val connectedDevice = deviceRepository.getConnectedDevice()
                val nextActivity = if (connectedDevice != null) {
                    DeviceDashboardActivity::class.java
                } else {
                    BluetoothActivity::class.java
                }
                
                val intent = Intent(this@WelcomeActivity, nextActivity)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            return
        }
        
        // Create root ConstraintLayout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
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
            
            // Bypass letterboxing by zooming the VideoView symmetrically
            videoView.post {
                val videoWidth = mp.videoWidth.toFloat()
                val videoHeight = mp.videoHeight.toFloat()
                if (videoWidth == 0f || videoHeight == 0f) return@post
                
                val videoRatio = videoWidth / videoHeight
                val screenRatio = videoView.width.toFloat() / videoView.height.toFloat()
                
                val scale = videoRatio / screenRatio
                if (scale >= 1f) {
                    videoView.scaleX = scale
                    videoView.scaleY = 1f
                } else {
                    videoView.scaleX = 1f
                    videoView.scaleY = 1f / scale
                }
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
        
        // 5. Circular arrow button
        val nextButton = Button(this).apply {
            id = View.generateViewId()
            text = "→"
            textSize = 24f
            setTextColor(Color.parseColor("#009688"))
            
            val circularBackground = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.BLACK)
            }
            background = circularBackground
            
            // Make it circular
            val size = dpToPx(64)
            layoutParams = ConstraintLayout.LayoutParams(size, size)
            
            setOnClickListener {
                val intent = Intent(this@WelcomeActivity, WelcomeActivity2::class.java)
                startActivity(intent)
            }
        }
        rootLayout.addView(nextButton)
        
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

        // Constrain "Welcoming you to" text
        constraintSet.connect(
            welcomingText.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
            dpToPx(90)
        )
        constraintSet.centerHorizontally(welcomingText.id, ConstraintSet.PARENT_ID)
        
        // Constrain "Vérité" text
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
        
        // Constrain circular button inside glass box near bottom
        constraintSet.connect(
            nextButton.id,
            ConstraintSet.BOTTOM,
            glassBox.id,
            ConstraintSet.BOTTOM,
            dpToPx(40)
        )
        constraintSet.centerHorizontally(nextButton.id, ConstraintSet.PARENT_ID)
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
