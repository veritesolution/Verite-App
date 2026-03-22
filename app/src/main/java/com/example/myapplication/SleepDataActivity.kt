package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.myapplication.data.logic.SleepStageAnalyzer
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.ui.components.SleepChartView
import com.example.myapplication.data.bluetooth.BioData

class SleepDataActivity : AppCompatActivity() {

    private lateinit var sleepStageAnalyzer: SleepStageAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_data)

        sleepStageAnalyzer = SleepStageAnalyzer(this)

        findViewById<ComposeView>(R.id.composeView).setContent {
            VeriteTheme {
                SkyBackground { }
            }
        }

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        findViewById<ImageView>(R.id.profileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        setupAnalysis()
    }

    private fun setupAnalysis() {
        val chartView = findViewById<SleepChartView>(R.id.sleepChartView)
        val mockPoints = mutableListOf<SleepChartView.SleepDataPoint>()
        
        val startTime = System.currentTimeMillis() - 8 * 3600 * 1000
        var currentTime = startTime
        val random = java.util.Random()
        
        for (i in 0 until 48) {
            val bioData = com.example.myapplication.data.bluetooth.BioData(
                heartRate = 55 + random.nextInt(20),
                alpha = 5f + random.nextFloat() * 10f,
                beta = 8f + random.nextFloat() * 5f,
                theta = 6f + random.nextFloat() * 12f,
                timestamp = currentTime
            )

            sleepStageAnalyzer.analyze(bioData)
            val predictedStage = sleepStageAnalyzer.currentStage.value

            mockPoints.add(SleepChartView.SleepDataPoint(currentTime, predictedStage))
            currentTime += 10 * 60 * 1000
        }
        
        chartView.setData(mockPoints)

        val deepCount = mockPoints.count { it.stage == SleepChartView.SleepStage.DEEP }
        val remCount = mockPoints.count { it.stage == SleepChartView.SleepStage.REM }
        
        findViewById<TextView>(R.id.tvTotalSleep).text = "8h 00m"
        findViewById<TextView>(R.id.tvDeepSleep).text = "${deepCount * 10 / 60}h ${deepCount * 10 % 60}m"
        findViewById<TextView>(R.id.tvRemSleep).text = "${remCount * 10 / 60}h ${remCount * 10 % 60}m"
        findViewById<TextView>(R.id.tvSleepEfficiency).text = "94% Efficiency"
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepStageAnalyzer.close()
    }
}
