package com.example.myapplication.data.bluetooth

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
import java.nio.charset.Charset
import java.util.UUID
import java.util.Random

data class BioData(
    val heartRate: Int,    // Mapped from Pulse
    val alpha: Float,
    val beta: Float,
    val theta: Float,
    val eeg: Float = 0f,   // Raw EEG from ESP32
    val emg: Float = 0f,   // Raw EMG from ESP32
    val pulse: Float = 0f, // Raw Pulse from ESP32
    val timestamp: Long = System.currentTimeMillis()
)

class BluetoothLeManager private constructor(private val context: Context) {

    private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val EEG_UUID     = UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123401")
    private val EMG_UUID     = UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123402")
    private val PULSE_UUID   = UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123403")
    private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _bioDataStream = MutableSharedFlow<BioData>()
    val bioDataStream: SharedFlow<BioData> = _bioDataStream

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var bluetoothGatt: BluetoothGatt? = null
    private val sensorDao = AppDatabase.getDatabase(context).sensorDao()
    
    private var currentBioData = BioData(0, 0f, 0f, 0f)

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    enableNotifications(gatt, service)
                }
            }
        }

        private fun enableNotifications(gatt: BluetoothGatt, service: BluetoothGattService) {
            val characteristics = listOf(EEG_UUID, EMG_UUID, PULSE_UUID)
            characteristics.forEach { uuid ->
                val char = service.getCharacteristic(uuid)
                if (char != null) {
                    gatt.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(CLIENT_CONFIG_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val stringValue = characteristic.getStringValue(0)
            val floatValue = stringValue?.toFloatOrNull() ?: return
            
            scope.launch {
                when (characteristic.uuid) {
                    EEG_UUID -> {
                        currentBioData = currentBioData.copy(eeg = floatValue)
                        sensorDao.insert(SensorReading("EEG", floatValue))
                    }
                    EMG_UUID -> {
                        currentBioData = currentBioData.copy(emg = floatValue)
                        sensorDao.insert(SensorReading("EMG", floatValue))
                    }
                    PULSE_UUID -> {
                        currentBioData = currentBioData.copy(pulse = floatValue, heartRate = (floatValue * 100).toInt())
                        sensorDao.insert(SensorReading("PULSE", floatValue))
                    }
                }
                _bioDataStream.emit(currentBioData.copy(timestamp = System.currentTimeMillis()))
            }
        }
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

    fun connect(address: String, isSimulated: Boolean = false) {
        if (isSimulated) {
            startSimulation()
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = adapter.getRemoteDevice(address)
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        stopSimulation()
        bluetoothGatt?.disconnect()
    }

    private fun startSimulation() {
        stopSimulation()
        _connectionState.value = ConnectionState.CONNECTING
        
        simulationJob = scope.launch {
            delay(1000)
            _connectionState.value = ConnectionState.CONNECTED
            
            val random = Random()
            while (isActive) {
                val eeg = 1.0f + random.nextFloat() * 2.0f
                val emg = 0.5f + random.nextFloat() * 1.5f
                val pulse = 0.8f + random.nextFloat() * 0.4f
                
                val data = BioData(
                    heartRate = (pulse * 100).toInt(),
                    alpha = 8f + random.nextFloat() * 4f,
                    beta = 13f + random.nextFloat() * 10f,
                    theta = 4f + random.nextFloat() * 4f,
                    eeg = eeg,
                    emg = emg,
                    pulse = pulse
                )
                
                sensorDao.insert(SensorReading("EEG", eeg))
                sensorDao.insert(SensorReading("EMG", emg))
                sensorDao.insert(SensorReading("PULSE", pulse))
                
                _bioDataStream.emit(data)
                delay(1000)
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }
}
