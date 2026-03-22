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
import android.widget.FrameLayout
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
            setBackgroundColor(Color.parseColor("#004d4d"))
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
        
        // Circular arrow button
        val nextButton = Button(this).apply {
            id = View.generateViewId()
            text = "→"
            textSize = 24f
            setTextColor(Color.parseColor("#009688"))
            setBackgroundColor(Color.BLACK)
            
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
        
        // Constrain "Welcoming you to" text
        constraintSet.connect(
            welcomingText.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
            dpToPx(200)
        )
        constraintSet.connect(
            welcomingText.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            0
        )
        constraintSet.connect(
            welcomingText.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            0
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
        
        // Constrain circular button
        constraintSet.connect(
            nextButton.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
            dpToPx(80)
        )
        constraintSet.centerHorizontally(nextButton.id, ConstraintSet.PARENT_ID)
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
