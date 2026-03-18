package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplication.data.local.AppDatabase
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HeadbandHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root Layout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundResource(R.drawable.group_1000006461)
        }

        // --- Header ---
        val backButton = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.parseColor("#00BFA5"))
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setOnClickListener { finish() }
        }
        rootLayout.addView(backButton)

        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            
            val spannable = SpannableString("Vérité")
            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#00BFA5")), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 2, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = spannable
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        }
        rootLayout.addView(titleText)

        val profileIcon = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_myplaces)
            setColorFilter(Color.WHITE)
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(40), dpToPx(40))
            setOnClickListener {
                startActivity(android.content.Intent(this@HeadbandHomeActivity, ProfileActivity::class.java))
            }
        }
        rootLayout.addView(profileIcon)

        // Observe User Profile
        val database = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            database.userDao().getUser().collect { user ->
                user?.profileImagePath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        profileIcon.clearColorFilter()
                        profileIcon.load(file) {
                            transformations(CircleCropTransformation())
                        }
                    }
                }
            }
        }

        // --- Central Headband ---
        val headbandImage = ImageView(this).apply {
            id = View.generateViewId()
            val resId = resources.getIdentifier("sleep_band", "drawable", packageName)
            if (resId != 0) setImageResource(resId) else setImageResource(android.R.drawable.ic_lock_idle_low_battery)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(280), dpToPx(180))
        }
        rootLayout.addView(headbandImage)

        // --- Nodes Data ---
        data class FeatureItem(val name: String, val angle: Float)
        val features = listOf(
            FeatureItem("TMR", 0f),
            FeatureItem("Sleep Data", 45f),
            FeatureItem("Bio Feedback", 135f),
            FeatureItem("Dream galore", 180f),
            FeatureItem("Alarm", 225f),
            FeatureItem("To-Do List", 315f)
        )

        val nodeViews = mutableListOf<View>()
        val labelViews = mutableListOf<View>()

        for (feature in features) {
            val nodeIcon = View(this).apply {
                id = View.generateViewId()
                background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
                    paint.color = Color.parseColor("#1A3E3E")
                    paint.alpha = 200
                }
                layoutParams = ConstraintLayout.LayoutParams(dpToPx(24), dpToPx(24))
            }
            rootLayout.addView(nodeIcon)
            nodeViews.add(nodeIcon)

            val label = TextView(this).apply {
                id = View.generateViewId()
                text = feature.name
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#121F1F"))
                    cornerRadius = dpToPx(16).toFloat()
                }
                layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            }
            rootLayout.addView(label)
            labelViews.add(label)
        }

        // --- Line View ---
        val lineView = object : View(this) {
            private val paint = Paint().apply {
                color = Color.WHITE
                strokeWidth = dpToPx(3).toFloat()
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = headbandImage.x + headbandImage.width / 2
                val cy = headbandImage.y + headbandImage.height / 2

                for (node in nodeViews) {
                    val nx = node.x + node.width / 2
                    val ny = node.y + node.height / 2
                    canvas.drawLine(cx, cy, nx, ny, paint)
                }
            }
        }.apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(0, 0)
        }
        rootLayout.addView(lineView, 0) // Add at index 0 to be behind everything

        // --- Adaptive Sound Button ---
        val adaptiveSoundBtn = android.widget.TextView(this).apply {
            id = View.generateViewId()
            text = "🎧  Adaptive Sound"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#004D5A"), Color.parseColor("#007A7A"))
            ).apply { cornerRadius = dpToPx(26).toFloat() }
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(220), dpToPx(52))
            setOnClickListener {
                startActivity(android.content.Intent(this@HeadbandHomeActivity, AdaptiveSoundActivity::class.java))
            }
        }
        rootLayout.addView(adaptiveSoundBtn)

        // --- Footer ---
        val footerText = android.widget.TextView(this).apply {
            id = View.generateViewId()
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            val textStr = "Vérité Sleep Band"
            val s = SpannableString(textStr)
            s.setSpan(ForegroundColorSpan(Color.parseColor("#00BFA5")), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(Color.WHITE), 6, textStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = s
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        }
        rootLayout.addView(footerText)

        // --- Constraints ---
        val set = ConstraintSet()
        set.clone(rootLayout)

        set.connect(backButton.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(32))
        set.connect(backButton.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, dpToPx(24))

        set.connect(titleText.id, ConstraintSet.TOP, backButton.id, ConstraintSet.TOP)
        set.connect(titleText.id, ConstraintSet.BOTTOM, backButton.id, ConstraintSet.BOTTOM)
        set.centerHorizontally(titleText.id, ConstraintSet.PARENT_ID)

        set.connect(profileIcon.id, ConstraintSet.TOP, backButton.id, ConstraintSet.TOP)
        set.connect(profileIcon.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, dpToPx(24))

        set.centerVertically(headbandImage.id, ConstraintSet.PARENT_ID)
        set.centerHorizontally(headbandImage.id, ConstraintSet.PARENT_ID)

        set.connect(lineView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(lineView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.connect(lineView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(lineView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        val radius = dpToPx(140)

        for (i in features.indices) {
            val feature = features[i]
            val node = nodeViews[i]
            val label = labelViews[i]

            set.constrainCircle(node.id, headbandImage.id, radius, feature.angle)

            // Constrain Label relative to Node "Outward"
            // Simple heuristic based on angle
            when {
                feature.angle > 315 || feature.angle <= 45 -> { // Top
                    set.connect(label.id, ConstraintSet.BOTTOM, node.id, ConstraintSet.TOP, dpToPx(8))
                    set.centerHorizontally(label.id, node.id)
                }
                feature.angle > 45 && feature.angle <= 135 -> { // Right
                    set.connect(label.id, ConstraintSet.START, node.id, ConstraintSet.END, dpToPx(8))
                    set.centerVertically(label.id, node.id)
                }
                feature.angle > 135 && feature.angle <= 225 -> { // Bottom
                    set.connect(label.id, ConstraintSet.TOP, node.id, ConstraintSet.BOTTOM, dpToPx(8))
                    set.centerHorizontally(label.id, node.id)
                }
                else -> { // Left
                    set.connect(label.id, ConstraintSet.END, node.id, ConstraintSet.START, dpToPx(8))
                    set.centerVertically(label.id, node.id)
                }
            }

            if (feature.name == "TMR") {
                val intent = android.content.Intent(this, TmrFeatureActivity::class.java)
                node.setOnClickListener { startActivity(intent) }
                label.setOnClickListener { startActivity(intent) }
            } else if (feature.name == "To-Do List") {
                val intent = android.content.Intent(this, TodoActivity::class.java)
                node.setOnClickListener { startActivity(intent) }
                label.setOnClickListener { startActivity(intent) }
            } else if (feature.name == "Sleep Data") {
                val intent = android.content.Intent(this, SleepDataActivity::class.java)
                node.setOnClickListener { startActivity(intent) }
                label.setOnClickListener { startActivity(intent) }
            } else if (feature.name == "Alarm") {
                val intent = android.content.Intent(this, AlarmActivity::class.java)
                node.setOnClickListener { startActivity(intent) }
                label.setOnClickListener { startActivity(intent) }
            } else if (feature.name == "Dream galore") {
                val intent = android.content.Intent(this, DreamJournalActivity::class.java)
                node.setOnClickListener { startActivity(intent) }
                label.setOnClickListener { startActivity(intent) }
            } else if (feature.name == "Bio Feedback") {
                val intent = android.content.Intent(this, BioFeedbackActivity::class.java)
                node.setOnClickListener { startActivity(intent) }
                label.setOnClickListener { startActivity(intent) }
            }
        }

        set.connect(footerText.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dpToPx(16))
        set.centerHorizontally(footerText.id, ConstraintSet.PARENT_ID)

        set.connect(adaptiveSoundBtn.id, ConstraintSet.BOTTOM, footerText.id, ConstraintSet.TOP, dpToPx(14))
        set.centerHorizontally(adaptiveSoundBtn.id, ConstraintSet.PARENT_ID)

        set.applyTo(rootLayout)
        setContentView(rootLayout)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
