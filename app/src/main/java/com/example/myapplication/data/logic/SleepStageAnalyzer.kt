package com.example.myapplication.data.logic

import android.content.Context
import com.example.myapplication.data.bluetooth.BioData
import com.example.myapplication.ui.components.SleepChartView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SleepStageAnalyzer(private val context: Context) {

    private var interpreter: Interpreter? = null

    private val _currentStage = MutableStateFlow(SleepChartView.SleepStage.AWAKE)
    val currentStage: StateFlow<SleepChartView.SleepStage> = _currentStage

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd("sleep_stage_model.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Analyzes bio-data using the TFLite model to predict the current sleep stage.
     * 
     * Model Input expectations:
     * - FloatArray[4]: [HeartRate, Alpha, Beta, Theta]
     * 
     * Model Output expectations:
     * - FloatArray[4]: Probabilities for [AWAKE, LIGHT, DEEP, REM]
     */
    fun analyze(data: BioData) {
        val interpreter = interpreter ?: return

        // Normalize inputs (Simple normalization for demo, real models often use z-score or min-max)
        val input = floatArrayOf(
            data.heartRate.toFloat() / 100f, 
            data.alpha / 20f,
            data.beta / 30f,
            data.theta / 15f
        )
        
        val output = Array(1) { FloatArray(4) }
        
        try {
            interpreter.run(arrayOf(input), output)
            
            // Get stage with highest probability
            val probabilities = output[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
            
            val stage = when (maxIndex) {
                0 -> SleepChartView.SleepStage.AWAKE
                1 -> SleepChartView.SleepStage.LIGHT
                2 -> SleepChartView.SleepStage.DEEP
                3 -> SleepChartView.SleepStage.REM
                else -> SleepChartView.SleepStage.AWAKE
            }
            
            _currentStage.value = stage
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
