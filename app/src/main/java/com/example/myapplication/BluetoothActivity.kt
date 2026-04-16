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
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.bluetooth.BluetoothLeManager
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.DeviceRepository
import com.example.myapplication.ui.components.VeriteAlert
import kotlinx.coroutines.launch

class BluetoothActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var deviceListContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var progressBarActive: ProgressBar
    private val discoveredDevices = mutableSetOf<String>()
    
    private lateinit var bluetoothLeManager: BluetoothLeManager
    private lateinit var deviceRepository: DeviceRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBluetoothDiscovery()
        } else {
            VeriteAlert.warning(this, "Permissions required for Bluetooth")
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
                            addDeviceItem(name, address)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    progressBarActive.visibility = View.VISIBLE
                    statusText.text = "Scanning for devices..."
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    progressBarActive.visibility = View.GONE
                    if (discoveredDevices.isEmpty()) {
                        statusText.text = "No devices found.\nTry Simulation Mode below."
                        addSimulationModeItem()
                    } else {
                        statusText.text = "Select a device to connect"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothLeManager = BluetoothLeManager.getInstance(this)
        val database = AppDatabase.getDatabase(this)
        deviceRepository = DeviceRepository(database.deviceDao())

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

        // Header Layout (Simplified for brevity, similar to before)
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

        val backButton = TextView(this).apply {
            id = View.generateViewId()
            text = "←"
            textSize = 24f
            setTextColor(Color.parseColor("#00BFA5"))
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener { finish() }
        }
        headerLayout.addView(backButton)

        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            text = "Vérité"
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(titleText)

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
        progressBarActive = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = View.generateViewId()
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(120), dpToPx(8))
        }
        rootLayout.addView(progressBarActive)

        // Status Text
        statusText = TextView(this).apply {
            id = View.generateViewId()
            text = "Bluetooth Settings"
            textSize = 18f
            setTextColor(Color.parseColor("#00BFA5"))
            gravity = Gravity.CENTER
        }
        rootLayout.addView(statusText)

        // Welcome Button (Action Button)
        val welcomeButton = Button(this).apply {
            id = View.generateViewId()
            text = "Go to Dashboard"
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
                startActivity(Intent(this@BluetoothActivity, DeviceDashboardActivity::class.java))
            }
        }
        rootLayout.addView(welcomeButton)

        // Set Constraints
        val set = ConstraintSet()
        set.clone(rootLayout)
        set.connect(headerLayout.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(40))
        set.connect(cardLayout.id, ConstraintSet.TOP, headerLayout.id, ConstraintSet.BOTTOM, dpToPx(44))
        set.connect(progressBarActive.id, ConstraintSet.TOP, cardLayout.id, ConstraintSet.BOTTOM, dpToPx(40))
        set.centerHorizontally(progressBarActive.id, ConstraintSet.PARENT_ID)
        set.connect(statusText.id, ConstraintSet.TOP, progressBarActive.id, ConstraintSet.BOTTOM, dpToPx(20))
        set.centerHorizontally(statusText.id, ConstraintSet.PARENT_ID)
        set.connect(welcomeButton.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dpToPx(40))
        set.setMargin(welcomeButton.id, ConstraintSet.START, dpToPx(32))
        set.setMargin(welcomeButton.id, ConstraintSet.END, dpToPx(32))
        set.applyTo(rootLayout)
        setContentView(rootLayout)

        // Observe Connection State
        lifecycleScope.launch {
            bluetoothLeManager.connectionState.collect { state ->
                when(state) {
                    BluetoothLeManager.ConnectionState.CONNECTED -> {
                        statusText.text = "Connected Successfully!"
                        progressBarActive.visibility = View.GONE
                        // Sync with DB (Simulation: mark ID 1 as connected)
                        deviceRepository.updateConnectionStatus(1, true)
                        VeriteAlert.success(this@BluetoothActivity, "Device Linked")
                    }
                    BluetoothLeManager.ConnectionState.CONNECTING -> {
                        statusText.text = "Connecting..."
                        progressBarActive.visibility = View.VISIBLE
                    }
                    else -> {}
                }
            }
        }

        // Register Receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(receiver, filter)

        checkPermissionsAndEnable()
    }

    private fun checkPermissionsAndEnable() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val needed = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) {
            startBluetoothDiscovery()
        } else {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startBluetoothDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothAdapter?.startDiscovery()
        }
    }

    private fun addDeviceItem(name: String, address: String) {
        val view = createItemView(name, "Available") {
            bluetoothLeManager.connect(address, isSimulated = false)
        }
        deviceListContainer.addView(view)
    }

    private fun addSimulationModeItem() {
        val view = createItemView("Simulation Mode (Virtual Band)", "Simulate") {
            bluetoothLeManager.connect("SIM:00:11:22", isSimulated = true)
        }
        deviceListContainer.addView(view)
    }

    private fun createItemView(name: String, status: String, onClick: () -> Unit): View {
        return RelativeLayout(this).apply {
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setOnClickListener { onClick() }
            
            val nameText = TextView(context).apply {
                id = View.generateViewId()
                text = name
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            addView(nameText)

            val statusText = TextView(context).apply {
                text = status
                textSize = 14f
                setTextColor(Color.parseColor("#00BFA5"))
                layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                }
            }
            addView(statusText)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
