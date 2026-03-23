package com.example.myapplication.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.SensorReading
import java.util.UUID
import java.util.Random

// ── BLE UUIDs — must match BioWearable firmware exactly ─────────────────────
private const val SERVICE_UUID_STR      = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
private const val RAW_STREAM_UUID_STR   = "beb5483e-36e1-4688-b7f5-ea07361b26a8"   // NOTIFY — 24-byte SensorPacket @ 250 Hz
private const val COMMANDS_UUID_STR     = "12345678-1234-5678-1234-56789abcdef0"     // WRITE  — ASCII commands app→device
private const val DSP_RESULTS_UUID_STR  = "abcd1234-ab12-cd34-ef56-abcdef012345"    // NOTIFY — 32-byte ProcessedPacket @ 2 Hz
private const val CCCD_UUID_STR         = "00002902-0000-1000-8000-00805f9b34fb"

private const val TAG = "BioWearableBLE"

/**
 * Core bio-sensor data from the BioWearable headband.
 * Contains both raw sensor values and DSP-processed metrics.
 * Backward-compatible: heartRate, alpha, beta, theta are still present for
 * existing consumers (SleepStageAnalyzer, StressDetectionEngine, etc.)
 */
data class BioData(
    // ── Legacy fields (backward-compatible) ─────────────────────
    val heartRate: Int,        // BPM from HRV processing
    val alpha: Float,          // EEG Alpha band power %
    val beta: Float,           // EEG Beta band power %
    val theta: Float,          // EEG Theta band power %
    val eeg: Float = 0f,       // Raw EEG1 ADC value (0-4095)
    val emg: Float = 0f,       // Raw EMG ADC value (0-4095)
    val pulse: Float = 0f,     // Raw Pulse ADC value (0-4095)
    val timestamp: Long = System.currentTimeMillis(),

    // ── EEG band powers (from ProcessedPacket) ──────────────────
    val delta: Float = 0f,     // Delta band power % (0.5-4 Hz)
    val gamma: Float = 0f,     // Gamma band power % (30-45 Hz)

    // ── EEG cognitive indices ───────────────────────────────────
    val focusIndex: Float = 0f,      // Beta/Theta ratio (>1.5 = focused)
    val relaxIndex: Float = 0f,      // Alpha/Beta ratio (>2.0 = relaxed)
    val drowsyIndex: Float = 0f,     // Theta/Alpha ratio (>1.5 = drowsy)

    // ── EMG metrics ─────────────────────────────────────────────
    val emgRms: Int = 0,             // RMS amplitude (ADC units)
    val emgMeanFreq: Int = 0,        // Mean power frequency Hz
    val emgZeroCrossRate: Int = 0,   // Zero crossing rate per 100ms
    val emgActive: Boolean = false,  // Muscle contraction detected
    val emgFatigue: Int = 0,         // Fatigue percentage 0-100

    // ── HRV metrics ─────────────────────────────────────────────
    val rmssd: Int = 0,        // RMSSD ms (parasympathetic indicator)
    val sdnn: Int = 0,         // SDNN ms
    val pnn50: Int = 0,        // pNN50 %
    val hrvStress: Int = 0,    // Stress index 0-100

    // ── IMU orientation ─────────────────────────────────────────
    val pitch: Float = 0f,     // Degrees
    val roll: Float = 0f,      // Degrees
    val yaw: Float = 0f,       // Degrees (drifts without magnetometer)
    val accelMag: Float = 0f,  // Acceleration magnitude in g

    // ── Raw IMU from SensorPacket ───────────────────────────────
    val accX: Float = 0f,
    val accY: Float = 0f,
    val accZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val imuTemp: Float = 0f,  // Die temperature Celsius

    // ── Status flags ────────────────────────────────────────────
    val eegArtefact: Boolean = false,
    val motionDetected: Boolean = false,
    val hrvValid: Boolean = false,
    val pulseContacted: Boolean = false,
    val sequenceNumber: Int = 0
)

class BluetoothLeManager private constructor(private val context: Context) {

    private val SERVICE_UUID      = UUID.fromString(SERVICE_UUID_STR)
    private val RAW_STREAM_UUID   = UUID.fromString(RAW_STREAM_UUID_STR)
    private val COMMANDS_UUID     = UUID.fromString(COMMANDS_UUID_STR)
    private val DSP_RESULTS_UUID  = UUID.fromString(DSP_RESULTS_UUID_STR)
    private val CCCD_UUID         = UUID.fromString(CCCD_UUID_STR)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _bioDataStream = MutableSharedFlow<BioData>(replay = 1)
    val bioDataStream: SharedFlow<BioData> = _bioDataStream

    // Separate stream for high-rate raw data (250 Hz) — UI should sample, not consume all
    private val _rawDataStream = MutableSharedFlow<BioData>(replay = 1, extraBufferCapacity = 64)
    val rawDataStream: SharedFlow<BioData> = _rawDataStream

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bluetoothGatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private val sensorDao = AppDatabase.getDatabase(context).sensorDao()

    // Latest accumulated data (raw + processed merged)
    @Volatile
    private var currentBioData = BioData(0, 0f, 0f, 0f)

    // Track pending descriptor writes for sequential subscription
    private val pendingDescriptorWrites = mutableListOf<BluetoothGattCharacteristic>()

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to BioWearable")
                    _connectionState.value = ConnectionState.CONNECTED
                    gatt.requestMtu(185) // Request larger MTU for 24/32 byte packets
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from BioWearable")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    commandChar = null
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "MTU changed to $mtu")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "BioWearable service not found")
                return
            }

            // Store command characteristic for sending LED/motor/inject commands
            commandChar = service.getCharacteristic(COMMANDS_UUID)

            // Subscribe to both NOTIFY characteristics sequentially
            // (BLE stack can only handle one descriptor write at a time)
            pendingDescriptorWrites.clear()
            service.getCharacteristic(DSP_RESULTS_UUID)?.let { pendingDescriptorWrites.add(it) }
            service.getCharacteristic(RAW_STREAM_UUID)?.let { pendingDescriptorWrites.add(it) }

            enableNextNotification(gatt)
        }

        @Suppress("DEPRECATION")
        private fun enableNextNotification(gatt: BluetoothGatt) {
            if (pendingDescriptorWrites.isEmpty()) {
                Log.i(TAG, "All notifications enabled — receiving live data")
                return
            }
            val char = pendingDescriptorWrites.removeAt(0)
            gatt.setCharacteristicNotification(char, true)
            char.getDescriptor(CCCD_UUID)?.let { desc ->
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // Continue subscribing to next characteristic
            enableNextNotification(gatt)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            when (characteristic.uuid) {
                RAW_STREAM_UUID -> parseRawSensorPacket(data)
                DSP_RESULTS_UUID -> parseProcessedPacket(data)
            }
        }
    }

    // ── Parse 24-byte SensorPacket (250 Hz) ─────────────────────────────────
    private fun parseRawSensorPacket(data: ByteArray) {
        if (data.size < 24) return

        val seq   = u16(data, 0)
        val eeg1  = u16(data, 2)
        val eeg2  = u16(data, 4)
        val emg   = u16(data, 6)
        val pulse = u16(data, 8)
        val ax    = s16(data, 10)
        val ay    = s16(data, 12)
        val az    = s16(data, 14)
        val gx    = s16(data, 16)
        val gy    = s16(data, 18)
        val gz    = s16(data, 20)
        val temp  = s16(data, 22)

        val axG   = ax / 16384.0f
        val ayG   = ay / 16384.0f
        val azG   = az / 16384.0f
        val gxDps = gx / 131.0f
        val gyDps = gy / 131.0f
        val gzDps = gz / 131.0f
        val tempC = temp / 340.0f + 36.53f

        currentBioData = currentBioData.copy(
            eeg = eeg1.toFloat(),
            emg = emg.toFloat(),
            pulse = pulse.toFloat(),
            accX = axG,
            accY = ayG,
            accZ = azG,
            gyroX = gxDps,
            gyroY = gyDps,
            gyroZ = gzDps,
            imuTemp = tempC,
            sequenceNumber = seq,
            timestamp = System.currentTimeMillis()
        )

        // Emit on raw stream (high rate — UI should sample)
        scope.launch {
            _rawDataStream.emit(currentBioData)
        }

        // Store sensor readings at reduced rate (every 250th sample = 1 Hz)
        if (seq % 250 == 0) {
            scope.launch {
                sensorDao.insert(SensorReading("EEG", eeg1.toFloat()))
                sensorDao.insert(SensorReading("EMG", emg.toFloat()))
                sensorDao.insert(SensorReading("PULSE", pulse.toFloat()))
            }
        }
    }

    // ── Parse 32-byte ProcessedPacket (2 Hz) ────────────────────────────────
    private fun parseProcessedPacket(data: ByteArray) {
        if (data.size < 32) return

        val e1Delta  = data[0].toInt() and 0xFF
        val e1Theta  = data[1].toInt() and 0xFF
        val e1Alpha  = data[2].toInt() and 0xFF
        val e1Beta   = data[3].toInt() and 0xFF
        val e1Gamma  = data[4].toInt() and 0xFF
        // bytes 5-9: EEG CH2 (reserved, always 0)
        val focusX10   = data[10].toInt() and 0xFF
        val relaxX10   = data[11].toInt() and 0xFF
        val drowsyX10  = data[12].toInt() and 0xFF
        val emgRms     = ((data[14].toInt() and 0xFF) shl 8) or (data[13].toInt() and 0xFF) // uint16 LE at offset 13
        val emgMf      = data[15].toInt() and 0xFF
        val emgZcr     = data[16].toInt() and 0xFF
        val emgActive  = (data[17].toInt() and 0xFF) != 0
        val emgFatigue = data[18].toInt() and 0xFF
        val bpm        = data[19].toInt() and 0xFF
        val rmssd      = data[20].toInt() and 0xFF
        val sdnn       = data[21].toInt() and 0xFF
        val pnn50      = data[22].toInt() and 0xFF
        val hrvStress  = data[23].toInt() and 0xFF
        val pitchX10   = s16(data, 24)
        val rollX10    = s16(data, 26)
        val yawX10     = s16(data, 28)
        val accelMagX10 = data[30].toInt() and 0xFF
        val flags      = data[31].toInt() and 0xFF

        currentBioData = currentBioData.copy(
            // Legacy fields updated from processed data
            heartRate = bpm,
            alpha = e1Alpha.toFloat(),
            beta = e1Beta.toFloat(),
            theta = e1Theta.toFloat(),

            // EEG bands
            delta = e1Delta.toFloat(),
            gamma = e1Gamma.toFloat(),

            // Cognitive indices
            focusIndex = focusX10 / 10.0f,
            relaxIndex = relaxX10 / 10.0f,
            drowsyIndex = drowsyX10 / 10.0f,

            // EMG
            emgRms = emgRms,
            emgMeanFreq = emgMf,
            emgZeroCrossRate = emgZcr,
            emgActive = emgActive,
            emgFatigue = emgFatigue,

            // HRV
            rmssd = rmssd,
            sdnn = sdnn,
            pnn50 = pnn50,
            hrvStress = hrvStress,

            // IMU orientation
            pitch = pitchX10 / 10.0f,
            roll = rollX10 / 10.0f,
            yaw = yawX10 / 10.0f,
            accelMag = accelMagX10 / 10.0f,

            // Flags
            eegArtefact = (flags and 0x01) != 0,
            motionDetected = (flags and 0x04) != 0,
            hrvValid = (flags and 0x08) != 0,
            pulseContacted = (flags and 0x10) != 0,

            timestamp = System.currentTimeMillis()
        )

        // Emit on main bio data stream (2 Hz — safe for UI consumption)
        scope.launch {
            _bioDataStream.emit(currentBioData)
        }
    }

    // ── Binary helpers (little-endian) ──────────────────────────────────────
    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)

    private fun s16(data: ByteArray, offset: Int): Int {
        val value = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
        return if (value >= 0x8000) value - 0x10000 else value
    }

    companion object {
        @Volatile
        private var INSTANCE: BluetoothLeManager? = null

        fun getInstance(context: Context): BluetoothLeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothLeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, isSimulated: Boolean = false) {
        if (isSimulated) {
            startSimulation()
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = adapter.getRemoteDevice(address)
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopSimulation()
        _connectionState.value = ConnectionState.DISCONNECTING
        bluetoothGatt?.disconnect()
    }

    /**
     * Send an ASCII command to the BioWearable headband.
     * Commands: 'R' toggle red LED, 'G' green, 'B' blue, 'W' all white,
     *           'M' motor on, 'X' motor off, 'O' all outputs off
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun sendCommand(command: String) {
        val gatt = bluetoothGatt ?: return
        val char = commandChar ?: return
        char.value = command.toByteArray(Charsets.US_ASCII)
        gatt.writeCharacteristic(char)
        Log.d(TAG, "Sent command: $command")
    }

    /** Toggle Red LED */
    fun toggleRedLed() = sendCommand("R")
    /** Toggle Green LED */
    fun toggleGreenLed() = sendCommand("G")
    /** Toggle Blue LED */
    fun toggleBlueLed() = sendCommand("B")
    /** All LEDs white */
    fun allLedsWhite() = sendCommand("W")
    /** Vibration motor ON */
    fun motorOn() = sendCommand("M")
    /** Vibration motor OFF */
    fun motorOff() = sendCommand("X")
    /** All outputs OFF */
    fun allOutputsOff() = sendCommand("O")

    /**
     * Start PPG injection mode (for testing).
     * After calling this, send numeric strings like "2048" to inject PPG samples.
     */
    fun startPpgInject() = sendCommand("INJECT_START")
    fun stopPpgInject() = sendCommand("INJECT_STOP")

    /** Start EEG injection mode. Send "E:2048" lines to inject. */
    fun startEegInject() = sendCommand("EEG_START")
    fun stopEegInject() = sendCommand("EEG_STOP")

    /** Start EMG injection mode. Send "M:2048" lines to inject. */
    fun startEmgInject() = sendCommand("EMG_START")
    fun stopEmgInject() = sendCommand("EMG_STOP")

    /** Inject a direct RR interval (ms) for HRV testing. */
    fun injectRR(rrMs: Float) = sendCommand("RR:$rrMs")

    // ── Simulation mode (for development without hardware) ──────────────────

    private fun startSimulation() {
        stopSimulation()
        _connectionState.value = ConnectionState.CONNECTING

        simulationJob = scope.launch {
            delay(1000)
            _connectionState.value = ConnectionState.CONNECTED

            val random = Random()
            while (isActive) {
                val eeg = 2048f + (random.nextFloat() - 0.5f) * 200f
                val emgVal = 2048f + (random.nextFloat() - 0.5f) * 100f
                val pulseVal = 2048f + (random.nextFloat() - 0.5f) * 300f

                val alphaVal = 15f + random.nextFloat() * 20f
                val betaVal = 10f + random.nextFloat() * 15f
                val thetaVal = 8f + random.nextFloat() * 12f
                val deltaVal = 20f + random.nextFloat() * 30f
                val gammaVal = 3f + random.nextFloat() * 5f
                val bpm = 60 + random.nextInt(30)

                val data = BioData(
                    heartRate = bpm,
                    alpha = alphaVal,
                    beta = betaVal,
                    theta = thetaVal,
                    delta = deltaVal,
                    gamma = gammaVal,
                    eeg = eeg,
                    emg = emgVal,
                    pulse = pulseVal,
                    focusIndex = betaVal / (thetaVal + 0.1f),
                    relaxIndex = alphaVal / (betaVal + 0.1f),
                    drowsyIndex = thetaVal / (alphaVal + 0.1f),
                    emgRms = (100 + random.nextInt(200)),
                    emgMeanFreq = (40 + random.nextInt(50)),
                    emgZeroCrossRate = (10 + random.nextInt(30)),
                    emgActive = random.nextFloat() > 0.7f,
                    emgFatigue = random.nextInt(30),
                    rmssd = (20 + random.nextInt(40)),
                    sdnn = (40 + random.nextInt(50)),
                    pnn50 = (5 + random.nextInt(20)),
                    hrvStress = (10 + random.nextInt(50)),
                    pitch = -5f + random.nextFloat() * 10f,
                    roll = -3f + random.nextFloat() * 6f,
                    yaw = random.nextFloat() * 360f,
                    accelMag = 0.95f + random.nextFloat() * 0.1f,
                    hrvValid = true,
                    pulseContacted = true
                )

                sensorDao.insert(SensorReading("EEG", eeg))
                sensorDao.insert(SensorReading("EMG", emgVal))
                sensorDao.insert(SensorReading("PULSE", pulseVal))

                _bioDataStream.emit(data)
                delay(500) // 2 Hz like real ProcessedPacket rate
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }
}
