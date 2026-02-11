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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.Device
import com.example.myapplication.data.model.DeviceType
import com.example.myapplication.data.repository.DeviceRepository
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DeviceDashboardActivity : AppCompatActivity() {

    private lateinit var cardsLayout: LinearLayout
    private lateinit var deviceRepository: DeviceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(this)
        deviceRepository = DeviceRepository(database.deviceDao())

        // Root Layout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#000000"))
        }

        // Header Layout
        val headerLayout = RelativeLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(16), dpToPx(40), dpToPx(16), dpToPx(16))
        }
        rootLayout.addView(headerLayout)

        // Profile Icon (Left)
        val profileIcon = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_myplaces)
            setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener {
                startActivity(Intent(this@DeviceDashboardActivity, ProfileActivity::class.java))
            }
        }
        headerLayout.addView(profileIcon)

        // Observe User Profile
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

        // App Title (Center)
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            
            val spannable = SpannableString("Vérité")
            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#00BFA5")), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 2, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            text = spannable
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
        }
        headerLayout.addView(titleText)

        // Settings Icon (Right)
        val settingsIcon = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }
        headerLayout.addView(settingsIcon)

        // "My Devices" Title
        val dashboardTitle = TextView(this).apply {
            id = View.generateViewId()
            text = "My Devices"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00BFA5"))
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(dashboardTitle)

        // Scrollable container for cards
        val scrollView = ScrollView(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                0
            )
        }
        rootLayout.addView(scrollView)

        cardsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(80))
        }
        scrollView.addView(cardsLayout)

        // Bottom Navigation Bar
        val bottomNav = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 3f
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                dpToPx(70)
            )
        }
        rootLayout.addView(bottomNav)

        fun addNavItem(iconResId: Int): View {
            val itemLayout = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                isFocusable = true
            }
            val icon = ImageView(this).apply {
                setImageResource(iconResId)
                setColorFilter(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                    gravity = Gravity.CENTER
                }
            }
            itemLayout.addView(icon)
            bottomNav.addView(itemLayout)
            return itemLayout
        }

        addNavItem(R.drawable.vector)
        val frameIcon = addNavItem(R.drawable.frame)
        val userIcon = addNavItem(R.drawable.user)

        frameIcon.setOnClickListener {
             startActivity(Intent(this, OrynActivity::class.java))
        }

        userIcon.setOnClickListener {
             startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Set Constraints
        val set = ConstraintSet()
        set.clone(rootLayout)

        set.connect(headerLayout.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(dashboardTitle.id, ConstraintSet.TOP, headerLayout.id, ConstraintSet.BOTTOM, dpToPx(20))
        set.centerHorizontally(dashboardTitle.id, ConstraintSet.PARENT_ID)
        set.connect(scrollView.id, ConstraintSet.TOP, dashboardTitle.id, ConstraintSet.BOTTOM, dpToPx(32))
        set.connect(scrollView.id, ConstraintSet.BOTTOM, bottomNav.id, ConstraintSet.TOP)
        set.connect(bottomNav.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

        set.applyTo(rootLayout)
        setContentView(rootLayout)

        // Load data
        lifecycleScope.launch {
            deviceRepository.allDevices.collect { devices ->
                if (devices.isEmpty()) {
                    deviceRepository.initializeSampleDevices()
                } else {
                    updateDeviceCards(devices)
                }
            }
        }
    }

    private fun updateDeviceCards(devices: List<Device>) {
        cardsLayout.removeAllViews()
        for (device in devices) {
            val card = createDeviceCard(device)
            cardsLayout.addView(card)
        }
    }

    private fun createDeviceCard(device: Device): View {
        val cardView = RelativeLayout(this).apply {
            setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#051212"))
                cornerRadius = dpToPx(32).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00BFA5"), 10f, 10f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(180)
            ).apply {
                setMargins(0, 0, 0, dpToPx(20))
            }
            
            setOnClickListener {
                val intent = when(device.type) {
                    DeviceType.SLEEP_BAND -> Intent(this@DeviceDashboardActivity, HeadbandHomeActivity::class.java)
                    DeviceType.SMART_BACKREST -> Intent(this@DeviceDashboardActivity, BackrestHomeActivity::class.java)
                }
                startActivity(intent)
            }
        }

        val imageResId = resources.getIdentifier(device.imageResource, "drawable", packageName)
        val deviceImage = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(if (imageResId != 0) imageResId else android.R.drawable.ic_menu_report_image)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = RelativeLayout.LayoutParams(dpToPx(120), dpToPx(120)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }
        cardView.addView(deviceImage)

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.END_OF, deviceImage.id)
                addRule(RelativeLayout.CENTER_VERTICAL)
                marginStart = dpToPx(24)
            }
        }
        cardView.addView(textLayout)

        val nameText = TextView(this).apply {
            text = device.name
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        textLayout.addView(nameText)

        val statusText = TextView(this).apply {
            text = if (device.isConnected) "Connected" else "Not Connected"
            textSize = 14f
            setTextColor(if (device.isConnected) Color.GREEN else Color.parseColor("#00BFA5"))
            setPadding(0, dpToPx(8), 0, 0)
        }
        textLayout.addView(statusText)

        return cardView
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
