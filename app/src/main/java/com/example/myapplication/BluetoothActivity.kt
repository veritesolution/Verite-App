package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var deviceListContainer: LinearLayout
    private lateinit var statusText: TextView
    private val discoveredDevices = mutableSetOf<String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBluetoothDiscovery()
        } else {
            Toast.makeText(this, "Permissions required for Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    
                    device?.let {
                        val name = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            it.name ?: "Unknown Device"
                        } else {
                            "Unknown Device"
                        }
                        val address = it.address
                        if (!discoveredDevices.contains(address)) {
                            discoveredDevices.add(address)
                            addDeviceItem(name, "Available")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d("Bluetooth", "Discovery Started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d("Bluetooth", "Discovery Finished")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

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
        val backButton = TextView(this).apply {
            id = View.generateViewId()
            text = "←"
            textSize = 24f
            setTextColor(Color.parseColor("#00BFA5"))
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { finish() }
        }
        headerLayout.addView(backButton)

        // App Title
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER

            val spannable = SpannableString("Vérité")
            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.parseColor("#00BFA5")), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 2, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            text = spannable
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(titleText)

        // Settings / Bluetooth Breadcrumb
        val settingsBreadcrumb = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(32), 0, dpToPx(32), 0)
        }
        rootLayout.addView(settingsBreadcrumb)

        val settingsBackArrow = TextView(this).apply {
            text = "‹"
            textSize = 28f
            setTextColor(Color.WHITE)
        }
        settingsBreadcrumb.addView(settingsBackArrow)

        val settingsText = TextView(this).apply {
            text = "Settings"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(8), 0, dpToPx(32), 0)
        }
        settingsBreadcrumb.addView(settingsText)

        val bluetoothLabelNav = TextView(this).apply {
            text = "Bluetooth"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        settingsBreadcrumb.addView(bluetoothLabelNav)

        // Main Bluetooth Card
        val cardLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A1F1F"))
                cornerRadius = dpToPx(24).toFloat()
            }
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(cardLayout)

        // Bluetooth Toggle Row
        val toggleRow = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            )
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dpToPx(12).toFloat()
            }
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
        }
        cardLayout.addView(toggleRow)

        val toggleLabel = TextView(this).apply {
            text = "Bluetooth"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }
        toggleRow.addView(toggleLabel)

        val bluetoothSwitch = Switch(this).apply {
            id = View.generateViewId()
            isChecked = bluetoothAdapter?.isEnabled == true
            
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkPermissionsAndEnable()
                } else {
                    disableBluetooth()
                }
            }
        }
        toggleRow.addView(bluetoothSwitch)

        val discoverableLabel = TextView(this).apply {
            text = "Now discoverable as \"Device\""
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(dpToPx(8), dpToPx(12), 0, dpToPx(24))
        }
        cardLayout.addView(discoverableLabel)

        val myDevicesHeader = TextView(this).apply {
            text = "AVAILABLE DEVICES"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dpToPx(8), 0, 0, dpToPx(8))
        }
        cardLayout.addView(myDevicesHeader)

        // Device List Container
        deviceListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dpToPx(16).toFloat()
            }
        }
        cardLayout.addView(deviceListContainer)

        // Progress Bar
        val progressBarContainer = View(this).apply {
            id = View.generateViewId()
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A3E3E"))
                cornerRadius = dpToPx(4).toFloat()
            }
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(120), dpToPx(4))
        }
        rootLayout.addView(progressBarContainer)

        val progressBarActive = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = View.generateViewId()
            isIndeterminate = true
            progressDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#00BFA5"))
                cornerRadius = dpToPx(4).toFloat()
            }
            visibility = View.GONE
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(120), dpToPx(8))
        }
        rootLayout.addView(progressBarActive)

        // Pairing Instructions
        statusText = TextView(this).apply {
            id = View.generateViewId()
            text = "Pair your device in the\nBluetooth Settings"
            textSize = 20f
            setTextColor(Color.parseColor("#00BFA5"))
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(statusText)

        // Team Message
        val teamMessage = TextView(this).apply {
            id = View.generateViewId()
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            
            val teamString = "A Message from the Team of Vérité"
            val spannable = SpannableString(teamString)
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BFA5")),
                teamString.indexOf("Vérité"),
                teamString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text = spannable
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(teamMessage)

        // Welcome Button
        val welcomeButton = Button(this).apply {
            id = View.generateViewId()
            text = "Welcome !"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00BFA5"))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(2), Color.parseColor("#00BFA5"))
                cornerRadius = dpToPx(20).toFloat()
            }
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                dpToPx(60)
            )
            setOnClickListener {
                val intent = Intent(this@BluetoothActivity, DeviceDashboardActivity::class.java)
                startActivity(intent)
            }
        }
        rootLayout.addView(welcomeButton)

        // Set Constraints
        val set = ConstraintSet()
        set.clone(rootLayout)

        set.connect(headerLayout.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(40))
        set.connect(settingsBreadcrumb.id, ConstraintSet.TOP, headerLayout.id, ConstraintSet.BOTTOM, dpToPx(20))
        set.connect(cardLayout.id, ConstraintSet.TOP, settingsBreadcrumb.id, ConstraintSet.BOTTOM, dpToPx(24))
        set.setMargin(cardLayout.id, ConstraintSet.START, dpToPx(20))
        set.setMargin(cardLayout.id, ConstraintSet.END, dpToPx(20))

        set.connect(progressBarContainer.id, ConstraintSet.TOP, cardLayout.id, ConstraintSet.BOTTOM, dpToPx(60))
        set.centerHorizontally(progressBarContainer.id, ConstraintSet.PARENT_ID)
        
        set.connect(progressBarActive.id, ConstraintSet.TOP, progressBarContainer.id, ConstraintSet.TOP, 0)
        set.centerHorizontally(progressBarActive.id, ConstraintSet.PARENT_ID)

        set.connect(statusText.id, ConstraintSet.TOP, progressBarContainer.id, ConstraintSet.BOTTOM, dpToPx(40))
        set.centerHorizontally(statusText.id, ConstraintSet.PARENT_ID)

        set.connect(teamMessage.id, ConstraintSet.BOTTOM, welcomeButton.id, ConstraintSet.TOP, dpToPx(32))
        set.centerHorizontally(teamMessage.id, ConstraintSet.PARENT_ID)

        set.connect(welcomeButton.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dpToPx(40))
        set.setMargin(welcomeButton.id, ConstraintSet.START, dpToPx(32))
        set.setMargin(welcomeButton.id, ConstraintSet.END, dpToPx(32))

        set.applyTo(rootLayout)
        setContentView(rootLayout)

        // Register Receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(receiver, filter)

        if (bluetoothAdapter?.isEnabled == true) {
            checkPermissionsAndEnable()
        }
    }

    private fun checkPermissionsAndEnable() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isEmpty()) {
            startBluetoothDiscovery()
        } else {
            requestPermissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    private fun startBluetoothDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return
        }
        
        if (bluetoothAdapter?.isEnabled == false) {
            // This is a simplified approach, usually you'd use startResolutionForResult
            Toast.makeText(this, "Please enable Bluetooth in settings", Toast.LENGTH_SHORT).show()
            return
        }

        statusText.text = "Scanning for devices..."
        deviceListContainer.removeAllViews()
        discoveredDevices.clear()
        bluetoothAdapter?.startDiscovery()
    }

    private fun disableBluetooth() {
        // We can't easily turn off Bluetooth programmatically in newer Android versions
        // but we can stop our discovery
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothAdapter?.cancelDiscovery()
        }
        statusText.text = "Bluetooth Disabled"
        deviceListContainer.removeAllViews()
        discoveredDevices.clear()
    }

    private fun addDeviceItem(name: String, status: String) {
        val row = RelativeLayout(this).apply {
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }
        
        val nameText = TextView(this).apply {
            id = View.generateViewId()
            text = name
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(nameText)

        val infoIcon = TextView(this).apply {
            id = View.generateViewId()
            text = "ⓘ"
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }
        row.addView(infoIcon)

        val statusText = TextView(this).apply {
            text = status
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_VERTICAL)
                addRule(RelativeLayout.LEFT_OF, infoIcon.id)
                marginEnd = dpToPx(12)
            }
        }
        row.addView(statusText)

        deviceListContainer.addView(row)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
