package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.ui.components.SleepChartView

class SleepDataActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_data)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        setupMockData()
    }

    private fun setupMockData() {
        val chartView = findViewById<SleepChartView>(R.id.sleepChartView)
        
        val startTime = System.currentTimeMillis() - 8 * 3600 * 1000
        val mockPoints = mutableListOf<SleepChartView.SleepDataPoint>()
        
        // Generate mock hypnogram points
        var currentTime = startTime
        val stages = listOf(
            SleepChartView.SleepStage.AWAKE,
            SleepChartView.SleepStage.LIGHT,
            SleepChartView.SleepStage.DEEP,
            SleepChartView.SleepStage.LIGHT,
            SleepChartView.SleepStage.REM,
            SleepChartView.SleepStage.LIGHT,
            SleepChartView.SleepStage.DEEP,
            SleepChartView.SleepStage.LIGHT,
            SleepChartView.SleepStage.REM,
            SleepChartView.SleepStage.LIGHT,
            SleepChartView.SleepStage.AWAKE
        )

        stages.forEachIndexed { index, stage ->
            mockPoints.add(SleepChartView.SleepDataPoint(currentTime, stage))
            currentTime += (30 + (Math.random() * 60)).toLong() * 60 * 1000 // 30-90 mins per stage
        }
        
        chartView.setData(mockPoints)

        // Update Text Stats
        findViewById<TextView>(R.id.tvTotalSleep).text = "7h 45m"
        findViewById<TextView>(R.id.tvDeepSleep).text = "2h 15m"
        findViewById<TextView>(R.id.tvRemSleep).text = "1h 50m"
        findViewById<TextView>(R.id.tvSleepEfficiency).text = "92% Efficiency"
    }
}
