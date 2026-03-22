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
                // Feed to engine
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
        val data = JSONObject().apply {
            put("stress", stress.toDouble())
            put("hr", 72) // Fallback or get from latest data
            put("sdnn", 45)
            put("rmssd", 38)
            put("ibis", JSONArray(listOf(830, 840, 820))) // Example
        }

        webView.post {
            webView.evaluateJavascript("window.updateHRV($data)", null)
        }
    }
}
