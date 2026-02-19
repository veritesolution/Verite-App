package com.example.myapplication.data.bluetooth

import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

data class BioData(
    val heartRate: Int,
    val alpha: Float,
    val beta: Float,
    val theta: Float,
    val timestamp: Long = System.currentTimeMillis()
)

class BluetoothLeManager private constructor(private val context: Context) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _bioDataStream = MutableSharedFlow<BioData>()
    val bioDataStream: SharedFlow<BioData> = _bioDataStream

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
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
        
        // Real BLE implementation would go here
        // For project scope, we mainly use simulation if hardware isn't present
        _connectionState.value = ConnectionState.CONNECTING
        scope.launch {
            delay(1500)
            _connectionState.value = ConnectionState.CONNECTED
            Log.d("BLE", "Connected to $address")
        }
    }

    fun disconnect() {
        stopSimulation()
        _connectionState.value = ConnectionState.DISCONNECTING
        scope.launch {
            delay(500)
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun startSimulation() {
        stopSimulation()
        _connectionState.value = ConnectionState.CONNECTING
        
        simulationJob = scope.launch {
            delay(1000)
            _connectionState.value = ConnectionState.CONNECTED
            
            val random = Random()
            while (isActive) {
                val data = BioData(
                    heartRate = 60 + random.nextInt(25),
                    alpha = 8f + random.nextFloat() * 4f,
                    beta = 13f + random.nextFloat() * 10f,
                    theta = 4f + random.nextFloat() * 4f
                )
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
