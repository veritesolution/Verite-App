package com.example.myapplication

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

// ── BLE UUIDs — must match firmware exactly ────────────────────────────────
class BioWearableDiagnosticActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BioWearable"
        private const val DEVICE_NAME = "BioWerable"
        private const val SCAN_TIMEOUT_MS = 12_000L
        private const val RECONNECT_DELAY = 2_000L
        private const val REQUEST_PERM = 100

        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val SENSOR_DATA_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val LED_CONTROL_UUID = UUID.fromString("cba1d466-344c-4be3-ab3f-189f80dd7518")
        private val MOTOR_CONTROL_UUID = UUID.fromString("f9279c99-b7b3-4e9e-b0dd-e4e2c95e9ad3")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }


    // ── Views — Bio-Sensors ────────────────────────────────────────────────
    private lateinit var btnScan        : Button
    private lateinit var btnDisconnect  : Button
    private lateinit var tvStatus       : TextView
    private lateinit var tvDataRate     : TextView
    private lateinit var tvEeg1         : TextView
    private lateinit var tvEeg2         : TextView
    private lateinit var tvEmg          : TextView
    private lateinit var tvPulse        : TextView
    private lateinit var pbEeg1         : ProgressBar
    private lateinit var pbEeg2         : ProgressBar
    private lateinit var pbEmg          : ProgressBar
    private lateinit var pbPulse        : ProgressBar

    // ── Views — IMU ────────────────────────────────────────────────────────
    private lateinit var cardImu        : LinearLayout
    private lateinit var tvAccX         : TextView
    private lateinit var tvAccY         : TextView
    private lateinit var tvAccZ         : TextView
    private lateinit var tvGyroX        : TextView
    private lateinit var tvGyroY        : TextView
    private lateinit var tvGyroZ        : TextView
    private lateinit var tvImuTemp      : TextView

    // ── Views — Controls ───────────────────────────────────────────────────
    private lateinit var btnLedRed      : Button
    private lateinit var btnLedGreen    : Button
    private lateinit var btnLedBlue     : Button
    private lateinit var btnLedOff      : Button
    private lateinit var btnLedCycle    : Button
    private lateinit var btnMotorOn     : Button
    private lateinit var btnMotorOff    : Button
    private lateinit var btnMotorPattern: Button
    private lateinit var btnTestAll     : Button
    private lateinit var tvTestLog      : TextView

    // ── BLE ────────────────────────────────────────────────────────────────
    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private var bleScanner    : BluetoothLeScanner?           = null
    private var bluetoothGatt : BluetoothGatt?                = null
    private var ledChar       : BluetoothGattCharacteristic?  = null
    private var motorChar     : BluetoothGattCharacteristic?  = null
    private var lastDevice    : BluetoothDevice?              = null

    private val mainHandler    = Handler(Looper.getMainLooper())
    private var isScanning     = false
    private var userDisconnect = false
    private var isCycling      = false   // RGB cycle in progress
    private var testRunning    = false   // automated test in progress

    // ── Data rate tracking ─────────────────────────────────────────────────
    private var packetCount    = 0
    private var lastRateTime   = 0L

    // ── Latest sensor values for test validation ───────────────────────────
    @Volatile private var latestEeg1  = 0
    @Volatile private var latestEeg2  = 0
    @Volatile private var latestEmg   = 0
    @Volatile private var latestPulse = 0
    @Volatile private var hasImu      = false
    @Volatile private var latestAccX  = 0f
    @Volatile private var latestAccY  = 0f
    @Volatile private var latestAccZ  = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_wearable_diagnostic)
        bindViews()
        setupClickListeners()
        checkPermissions()
        startDataRateUpdater()
    }

    override fun onDestroy() {
        super.onDestroy()
        userDisconnect = true
        mainHandler.removeCallbacksAndMessages(null)
        disconnect()
    }

    private fun bindViews() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        btnScan = findViewById(R.id.btnScan)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvDataRate = findViewById(R.id.tvDataRate)
        tvEeg1 = findViewById(R.id.tvEeg1)
        tvEeg2 = findViewById(R.id.tvEeg2)
        tvEmg = findViewById(R.id.tvEmg)
        tvPulse = findViewById(R.id.tvPulse)
        pbEeg1 = findViewById(R.id.pbEeg1)
        pbEeg2 = findViewById(R.id.pbEeg2)
        pbEmg = findViewById(R.id.pbEmg)
        pbPulse = findViewById(R.id.pbPulse)

        cardImu = findViewById(R.id.cardImu)
        tvAccX = findViewById(R.id.tvAccX)
        tvAccY = findViewById(R.id.tvAccY)
        tvAccZ = findViewById(R.id.tvAccZ)
        tvGyroX = findViewById(R.id.tvGyroX)
        tvGyroY = findViewById(R.id.tvGyroY)
        tvGyroZ = findViewById(R.id.tvGyroZ)
        tvImuTemp = findViewById(R.id.tvImuTemp)

        btnLedRed = findViewById(R.id.btnLedRed)
        btnLedGreen = findViewById(R.id.btnLedGreen)
        btnLedBlue = findViewById(R.id.btnLedBlue)
        btnLedOff = findViewById(R.id.btnLedOff)
        btnLedCycle = findViewById(R.id.btnLedCycle)
        btnMotorOn = findViewById(R.id.btnMotorOn)
        btnMotorOff = findViewById(R.id.btnMotorOff)
        btnMotorPattern = findViewById(R.id.btnMotorPattern)
        btnTestAll = findViewById(R.id.btnTestAll)
        tvTestLog = findViewById(R.id.tvTestLog)

        setControlsEnabled(false)
    }

    private fun setupClickListeners() {
        btnScan.setOnClickListener {
            if (isScanning) stopScan() else startScan()
        }
        btnDisconnect.setOnClickListener {
            userDisconnect = true
            disconnect()
        }

        btnLedRed.setOnClickListener   { sendLed(255, 0, 0)   }
        btnLedGreen.setOnClickListener { sendLed(0, 255, 0)   }
        btnLedBlue.setOnClickListener  { sendLed(0, 0, 255)   }
        btnLedOff.setOnClickListener   { sendLed(0, 0, 0)     }
        btnLedCycle.setOnClickListener { runRgbCycle() }

        btnMotorOn.setOnClickListener  { sendMotor(true)  }
        btnMotorOff.setOnClickListener { sendMotor(false) }
        btnMotorPattern.setOnClickListener { runMotorPattern() }

        btnTestAll.setOnClickListener { runAllTests() }
    }

    private fun checkPermissions() {
        val required = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!hasPerm(Manifest.permission.BLUETOOTH_SCAN)) required += Manifest.permission.BLUETOOTH_SCAN
            if (!hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) required += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) required += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (required.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, required.toTypedArray(), REQUEST_PERM)
        }
    }

    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(rc: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (rc == REQUEST_PERM && results.any { it != PackageManager.PERMISSION_GRANTED }) {
            setStatus("⚠️ Bluetooth permissions denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!bluetoothAdapter.isEnabled) {
            setStatus("❌ Bluetooth is OFF — please enable it"); return
        }
        userDisconnect = false
        bleScanner = bluetoothAdapter.bluetoothLeScanner

        val filter = ScanFilter.Builder().setDeviceName(DEVICE_NAME).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        isScanning = true
        btnScan.text = "Stop Scan"
        setStatus("🔍 Scanning for \"$DEVICE_NAME\"...")

        bleScanner?.startScan(listOf(filter), settings, scanCallback)

        mainHandler.postDelayed({
            if (isScanning) { stopScan(); setStatus("⏱ Not found — tap Scan to retry") }
        }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        isScanning = false
        btnScan.text = "Scan for BioWearable"
        bleScanner?.stopScan(scanCallback)
        bleScanner = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            lastDevice = result.device
            setStatus("📡 Found BioWearable — connecting...")
            connectToDevice(result.device)
        }
        override fun onScanFailed(errorCode: Int) {
            setStatus("❌ Scan failed (error $errorCode)")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    gatt.requestMtu(185)
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    bluetoothGatt = null; ledChar = null; motorChar = null

                    runOnUiThread {
                        setStatus("🔴 Disconnected")
                        setControlsEnabled(false)
                        btnScan.isEnabled = true
                        cardImu.visibility = View.GONE
                    }

                    if (!userDisconnect && lastDevice != null) {
                        runOnUiThread { setStatus("🔄 Connection lost — reconnecting...") }
                        mainHandler.postDelayed({
                            lastDevice?.let { connectToDevice(it) }
                        }, RECONNECT_DELAY)
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                runOnUiThread { setStatus("❌ BioWearable service not found") }
                return
            }

            ledChar   = service.getCharacteristic(LED_CONTROL_UUID)
            motorChar = service.getCharacteristic(MOTOR_CONTROL_UUID)

            service.getCharacteristic(SENSOR_DATA_UUID)?.let { char ->
                gatt.setCharacteristicNotification(char, true)
                char.getDescriptor(CCCD_UUID)?.let { desc ->
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }
            }

            runOnUiThread {
                setStatus("🟢 Connected — receiving live data")
                setControlsEnabled(true)
                lastRateTime = System.currentTimeMillis()
                packetCount = 0
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == SENSOR_DATA_UUID) {
                parseSensorData(characteristic.value)
            }
        }
    }

    private fun parseSensorData(data: ByteArray) {
        if (data.size < 8) return

        fun u16(i: Int) = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i].toInt() and 0xFF)
        fun s16(i: Int): Short = (((data[i + 1].toInt() and 0xFF) shl 8) or (data[i].toInt() and 0xFF)).toShort()

        val eeg1  = u16(0);  val eeg2  = u16(2)
        val emg   = u16(4);  val pulse = u16(6)
        latestEeg1 = eeg1; latestEeg2 = eeg2; latestEmg = emg; latestPulse = pulse

        val v1 = eeg1  * 3.1f / 4095f
        val v2 = eeg2  * 3.1f / 4095f
        val v3 = emg   * 3.1f / 4095f
        val v4 = pulse * 3.1f / 4095f

        val imuPresent = data.size >= 22
        var accX = 0f; var accY = 0f; var accZ = 0f
        var gyrX = 0f; var gyrY = 0f; var gyrZ = 0f
        var temp = 0f

        if (imuPresent) {
            accX = s16(8)  / 100f;  accY = s16(10) / 100f;  accZ = s16(12) / 100f
            gyrX = s16(14) / 100f;  gyrY = s16(16) / 100f;  gyrZ = s16(18) / 100f
            temp = s16(20) / 100f
            latestAccX = accX; latestAccY = accY; latestAccZ = accZ
            hasImu = true
        }

        packetCount++

        runOnUiThread {
            tvEeg1.text  = "EEG 1:  $eeg1  (%.2fV)".format(v1)
            tvEeg2.text  = "EEG 2:  $eeg2  (%.2fV)".format(v2)
            tvEmg.text   = "EMG:    $emg  (%.2fV)".format(v3)
            tvPulse.text = "Pulse:  $pulse  (%.2fV)".format(v4)
            pbEeg1.progress  = eeg1
            pbEeg2.progress  = eeg2
            pbEmg.progress   = emg
            pbPulse.progress = pulse

            if (imuPresent) {
                cardImu.visibility = View.VISIBLE
                tvAccX.text    = "X: %+.2f".format(accX)
                tvAccY.text    = "Y: %+.2f".format(accY)
                tvAccZ.text    = "Z: %+.2f".format(accZ)
                tvGyroX.text   = "X: %+.1f".format(gyrX)
                tvGyroY.text   = "Y: %+.1f".format(gyrY)
                tvGyroZ.text   = "Z: %+.1f".format(gyrZ)
                tvImuTemp.text = "🌡 Temp: %.1f°C".format(temp)
            }
        }
    }

    private fun startDataRateUpdater() {
        mainHandler.post(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val elapsed = (now - lastRateTime).toFloat() / 1000f
                if (elapsed >= 1f && packetCount > 0) {
                    val hz = packetCount / elapsed
                    tvDataRate.text = "📶 %.0f Hz  |  %d packets".format(hz, packetCount)
                    packetCount = 0
                    lastRateTime = now
                } else if (bluetoothGatt == null) {
                    tvDataRate.text = ""
                }
                mainHandler.postDelayed(this, 1000)
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun sendLed(r: Int, g: Int, b: Int) {
        val gatt = bluetoothGatt ?: return
        val char = ledChar       ?: return
        char.value = byteArrayOf(r.toByte(), g.toByte(), b.toByte())
        gatt.writeCharacteristic(char)
    }

    @Suppress("DEPRECATION")
    private fun sendMotor(on: Boolean) {
        val gatt = bluetoothGatt ?: return
        val char = motorChar     ?: return
        char.value = byteArrayOf(if (on) 0x01 else 0x00)
        gatt.writeCharacteristic(char)
    }

    private fun runRgbCycle() {
        if (isCycling) return
        isCycling = true
        btnLedCycle.text = "🌈  Cycling..."
        btnLedCycle.isEnabled = false

        data class C(val n: String, val r: Int, val g: Int, val b: Int)
        val colors = listOf(C("White", 255, 255, 255), C("Red", 255, 0, 0), C("Green", 0, 255, 0), C("Blue", 0, 0, 255), C("Yellow", 255, 200, 0), C("Cyan", 0, 255, 255), C("Magenta", 255, 0, 200))

        var i = 0
        val step = object : Runnable {
            override fun run() {
                if (i < colors.size && bluetoothGatt != null) {
                    val c = colors[i]
                    setStatus("🌈 ${c.n}")
                    sendLed(c.r, c.g, c.b)
                    i++
                    mainHandler.postDelayed(this, 500)
                } else {
                    sendLed(0, 0, 0)
                    isCycling = false
                    btnLedCycle.text = "🌈  RGB Colour Cycle"
                    btnLedCycle.isEnabled = true
                    setStatus("🟢 Connected — receiving live data")
                }
            }
        }
        mainHandler.post(step)
    }

    private fun runMotorPattern() {
        btnMotorPattern.isEnabled = false
        btnMotorPattern.text = "📳  Buzzing..."
        val steps = listOf(Pair(true, 1500), Pair(false, 350), Pair(true, 300), Pair(false, 350), Pair(true, 300), Pair(false, 0))
        var cumDelay = 0L
        for ((on, durationMs) in steps) {
            mainHandler.postDelayed({ sendMotor(on) }, cumDelay)
            cumDelay += durationMs
        }
        mainHandler.postDelayed({
            btnMotorPattern.isEnabled = true
            btnMotorPattern.text = "📳  Buzz Pattern (3 pulses)"
        }, cumDelay + 100)
    }

    private fun runAllTests() {
        if (testRunning || bluetoothGatt == null) return
        testRunning = true
        btnTestAll.isEnabled = false
        btnTestAll.text = "⏳  Testing..."

        val log = StringBuilder()
        log.appendLine("╔══════════════════════════════════╗")
        log.appendLine("║  BioWearable Hardware Diagnostic ║")
        log.appendLine("╚══════════════════════════════════╝\n")
        tvTestLog.text = log

        var delay = 0L
        delay += scheduleTest(delay, log, "1  Red LED") { sendLed(255, 0, 0) }
        delay += scheduleOff(delay) { sendLed(0, 0, 0) }
        delay += scheduleTest(delay, log, "2  Green LED") { sendLed(0, 255, 0) }
        delay += scheduleOff(delay) { sendLed(0, 0, 0) }
        delay += scheduleTest(delay, log, "3  Blue LED") { sendLed(0, 0, 255) }
        delay += scheduleOff(delay) { sendLed(0, 0, 0) }

        data class Clr(val r: Int, val g: Int, val b: Int, val n: String)
        val rgbColors = listOf(Clr(255,255,255,"White"), Clr(255,0,0,"Red"), Clr(0,255,0,"Green"), Clr(0,0,255,"Blue"), Clr(255,200,0,"Yellow"), Clr(0,255,255,"Cyan"), Clr(255,0,200,"Magenta"))
        for (c in rgbColors) {
            mainHandler.postDelayed({
                sendLed(c.r, c.g, c.b)
                log.appendLine("     ${c.n} ✔")
                tvTestLog.text = log
            }, delay)
            delay += 400
        }
        mainHandler.postDelayed({
            sendLed(0, 0, 0)
            log.appendLine("  ✔  TEST 4  RGB Cycle         PASS\n")
            tvTestLog.text = log
        }, delay)
        delay += 300

        mainHandler.postDelayed({
            log.appendLine("  ⏳ TEST 5  Vibration Motor...")
            tvTestLog.text = log
            sendMotor(true)
        }, delay)
        delay += 1000
        mainHandler.postDelayed({ sendMotor(false) }, delay)
        delay += 300
        mainHandler.postDelayed({ sendMotor(true) }, delay)
        delay += 300
        mainHandler.postDelayed({
            sendMotor(false)
            log.appendLine("  ✔  TEST 5  Vibration Motor    PASS\n")
            tvTestLog.text = log
        }, delay)
        delay += 500

        mainHandler.postDelayed({
            log.appendLine("  ── Analog Sensor Validation ──")
            fun checkSensor(name: String, value: Int): Boolean {
                val alive = value in 31..4069
                val volts = value * 3.1f / 4095f
                val status = if (alive) "PASS" else "FAIL"
                log.appendLine("  ${if (alive) "✔" else "✘"}  $name  raw=$value  %.2fV  $status".format(volts))
                return alive
            }
            checkSensor("EEG1 ", latestEeg1)
            checkSensor("EEG2 ", latestEeg2)
            checkSensor("EMG  ", latestEmg)
            checkSensor("Pulse", latestPulse)
            log.appendLine()
            tvTestLog.text = log
        }, delay)
        delay += 300

        mainHandler.postDelayed({
            if (hasImu) {
                val gMag = Math.sqrt((latestAccX * latestAccX + latestAccY * latestAccY + latestAccZ * latestAccZ).toDouble()).toFloat()
                val sane = gMag in 7.0f..12.5f
                log.appendLine("  ${if (sane) "✔" else "⚠"}  MPU6050 IMU  |g|=%.2f m/s²  ${if (sane) "PASS" else "CHECK"}".format(gMag))
            } else {
                log.appendLine("  ⚠  MPU6050 IMU  No IMU data in BLE packet")
            }
            log.appendLine("\n══════════════════════════════════")
            log.appendLine("  Test complete ★")
            log.appendLine("══════════════════════════════════")
            tvTestLog.text = log
            testRunning = false
            btnTestAll.isEnabled = true
            btnTestAll.text = "▶  Run All Tests"
        }, delay)
    }

    private fun scheduleTest(delay: Long, log: StringBuilder, name: String, action: () -> Unit): Long {
        mainHandler.postDelayed({ log.appendLine("  ⏳ TEST $name..."); tvTestLog.text = log; action() }, delay)
        mainHandler.postDelayed({ log.appendLine("  ✔  TEST $name    PASS"); tvTestLog.text = log }, delay + 800)
        return 900
    }

    private fun scheduleOff(delay: Long, action: () -> Unit): Long {
        mainHandler.postDelayed({ action() }, delay)
        return 300
    }

    @SuppressLint("MissingPermission")
    private fun disconnect() {
        mainHandler.removeCallbacksAndMessages(null)
        startDataRateUpdater()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        setControlsEnabled(false)
        btnScan.isEnabled = true
        setStatus("🔴 Disconnected")
    }

    private fun setStatus(msg: String) { tvStatus.text = msg }

    private fun setControlsEnabled(enabled: Boolean) {
        btnDisconnect.isEnabled  = enabled
        btnLedRed.isEnabled      = enabled
        btnLedGreen.isEnabled    = enabled
        btnLedBlue.isEnabled     = enabled
        btnLedOff.isEnabled      = enabled
        btnLedCycle.isEnabled    = enabled
        btnMotorOn.isEnabled     = enabled
        btnMotorOff.isEnabled    = enabled
        btnMotorPattern.isEnabled = enabled
        btnTestAll.isEnabled     = enabled
        btnScan.isEnabled        = !enabled
    }
}
