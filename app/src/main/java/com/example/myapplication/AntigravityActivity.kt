package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.bluetooth.BluetoothLeManager
import com.example.myapplication.data.logic.StressDetectionEngine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AntigravityActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bluetoothLeManager: BluetoothLeManager
    private val stressDetectionEngine = StressDetectionEngine()

    // Cache latest BioData for pushing complete HRV data to web
    @Volatile
    private var latestBioData: com.example.myapplication.data.bluetooth.BioData? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_antigravity)

        webView = findViewById(R.id.webView)
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        setupWebView()

        bluetoothLeManager = BluetoothLeManager.getInstance(this)

        observeBioData()
        observeStressLevels()
    }

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.databaseEnabled = true
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Page loaded
            }
        }

        // Load the local React app
        webView.loadUrl("file:///android_asset/antigravity/index.html")
    }

    private fun observeBioData() {
        lifecycleScope.launch {
            bluetoothLeManager.bioDataStream.collect { data ->
                latestBioData = data
                stressDetectionEngine.analyze(data)
            }
        }
    }

    private fun observeStressLevels() {
        lifecycleScope.launch {
            stressDetectionEngine.currentStress.collect { state ->
                // Push data to JS
                val stressVal = state.score / 100.0f
                
                // We don't have all HRV metrics in StressState, 
                // but we can pass what we have and mock the rest if needed, 
                // or extend StressDetectionEngine to provide more.
                // For now, let's pass the stress score which is primary.
                
                pushDataToWeb(stressVal)
            }
        }
    }

    private fun pushDataToWeb(stress: Float) {
        val bio = latestBioData
        val data = JSONObject().apply {
            put("stress", stress.toDouble())
            put("hr", bio?.heartRate ?: 0)
            put("sdnn", bio?.sdnn ?: 0)
            put("rmssd", bio?.rmssd ?: 0)
            put("pnn50", bio?.pnn50 ?: 0)
            put("hrvStress", bio?.hrvStress ?: 0)

            // EEG band powers for visualization
            put("alpha", (bio?.alpha ?: 0f).toDouble())
            put("beta", (bio?.beta ?: 0f).toDouble())
            put("theta", (bio?.theta ?: 0f).toDouble())
            put("delta", (bio?.delta ?: 0f).toDouble())
            put("gamma", (bio?.gamma ?: 0f).toDouble())

            // Cognitive indices
            put("focusIndex", (bio?.focusIndex ?: 0f).toDouble())
            put("relaxIndex", (bio?.relaxIndex ?: 0f).toDouble())
            put("drowsyIndex", (bio?.drowsyIndex ?: 0f).toDouble())

            // IMU orientation
            put("pitch", (bio?.pitch ?: 0f).toDouble())
            put("roll", (bio?.roll ?: 0f).toDouble())

            // Approximate IBI from HR (60000 / BPM) for heart coherence display
            val hr = bio?.heartRate ?: 72
            val ibiMs = if (hr > 0) 60000 / hr else 830
            put("ibis", JSONArray(listOf(ibiMs, ibiMs + 10, ibiMs - 10)))
        }

        webView.post {
            webView.evaluateJavascript("window.updateHRV($data)", null)
        }
    }
}
