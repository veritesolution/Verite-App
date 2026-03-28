package com.example.myapplication

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.remote.PsychNetworkModule
import com.example.myapplication.data.repository.PsychRepository
import com.example.myapplication.data.repository.PsychResult
import com.example.myapplication.util.ProfileIconHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "OrynActivity"

class OrynActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var etChatInput: EditText
    private var progressBar: ProgressBar? = null

    // Psychologist API
    private var psychRepository: PsychRepository? = null
    private var psychSessionId: String? = null
    private var apiAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oryn)

        val database = AppDatabase.getDatabase(this)

        // Initialize Psychologist API
        initPsychApi()

        setupUI(database)
        setupChat(database)

        // Initial greeting
        addMessage(ChatMessage("Hello! I am Oryn, your AI mental wellness companion. How can I help you today?", isUser = false))
    }

    /**
     * Generate a stable device-based username for auto-registration
     * with the Verite backend. Uses Android ID for uniqueness.
     */
    private fun getDeviceUsername(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        return "verite_${androidId?.take(12) ?: "default"}"
    }

    /**
     * Auto-authenticate with the Verite production backend.
     * Tries login first; if user doesn't exist, registers automatically.
     * This is seamless — the user never sees a login screen for Oryn.
     */
    private suspend fun autoAuthenticate(): Boolean {
        val repo = psychRepository ?: return false
        val tokenManager = PsychNetworkModule.tokenManager

        // If already logged in with valid tokens, skip auth
        if (tokenManager.isLoggedIn) {
            Log.d(TAG, "Already authenticated, skipping auto-auth")
            return true
        }

        val username = getDeviceUsername()
        val password = "verite_secure_${username}_2026"

        // Try login first
        when (val loginResult = repo.login(username, password)) {
            is PsychResult.Success -> {
                Log.i(TAG, "Auto-login successful for $username")
                return true
            }
            is PsychResult.Error -> {
                Log.d(TAG, "Login failed (${loginResult.message}), trying registration...")
            }
            else -> {}
        }

        // Login failed — try register (user might not exist yet)
        when (val regResult = repo.register(username, password, "Verite User")) {
            is PsychResult.Success -> {
                Log.i(TAG, "Auto-registration successful for $username")
                return true
            }
            is PsychResult.Error -> {
                // If registration says "already exists", try login again
                if (regResult.message.contains("exist", ignoreCase = true) ||
                    regResult.message.contains("taken", ignoreCase = true) ||
                    regResult.code == 400
                ) {
                    when (val retryLogin = repo.login(username, password)) {
                        is PsychResult.Success -> {
                            Log.i(TAG, "Retry login successful for $username")
                            return true
                        }
                        else -> {
                            Log.e(TAG, "Auto-auth completely failed for $username")
                        }
                    }
                } else {
                    Log.e(TAG, "Auto-registration failed: ${regResult.message}")
                }
            }
            else -> {}
        }

        return false
    }

    private fun initPsychApi() {
        try {
            PsychNetworkModule.init(this)
            psychRepository = PsychRepository()

            // Check server health, auto-authenticate, then mark API ready
            lifecycleScope.launch {
                repeat(2) { attempt ->
                    if (!isNetworkAvailable()) {
                        Log.w(TAG, "No network available (attempt ${attempt + 1})")
                        apiAvailable = false
                        if (attempt == 0) delay(2000)
                        return@repeat
                    }
                    when (val result = psychRepository?.healthCheck()) {
                        is PsychResult.Success -> {
                            Log.i(TAG, "Verite API online: ${result.data.version} | LLM: ${result.data.llmProvider}")
                            // Server is up — now auto-authenticate
                            val authOk = autoAuthenticate()
                            if (authOk) {
                                apiAvailable = true
                                Log.i(TAG, "Oryn fully connected and authenticated")
                            } else {
                                Log.w(TAG, "Server online but authentication failed")
                                apiAvailable = true // Still allow unauthenticated health checks
                            }
                            return@launch
                        }
                        is PsychResult.Error -> {
                            Log.w(TAG, "Verite API unavailable (attempt ${attempt + 1}): ${result.message}")
                            apiAvailable = false
                            if (attempt == 0) delay(2000)
                        }
                        else -> {
                            apiAvailable = false
                            if (attempt == 0) delay(2000)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize Verite API", e)
            apiAvailable = false
        }
    }

    private fun setupUI(database: AppDatabase) {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val profileIcon = findViewById<ImageView>(R.id.profileIcon)
        profileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        ProfileIconHelper.syncProfileIcon(this, profileIcon)

        val headerTitle = findViewById<android.widget.TextView>(R.id.headerTitle)
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(headerTitle)

        findViewById<Button>(R.id.btnCheckSummary).setOnClickListener {
            startActivity(Intent(this, OrynSummaryActivity::class.java))
        }

        // Optional progress bar (if layout has one)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupChat(database: AppDatabase) {
        recyclerView = findViewById(R.id.chatRecyclerView)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager

        adapter = ChatAdapter(messages, database)
        recyclerView.adapter = adapter

        etChatInput = findViewById(R.id.etChatInput)
        val btnSend = findViewById<ImageView>(R.id.btnSend)

        val sendMessageAction = {
            val text = etChatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                addMessage(ChatMessage(text, isUser = true))
                etChatInput.text.clear()
                sendToPsychApi(text)
            }
        }

        btnSend.setOnClickListener { sendMessageAction() }

        etChatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessageAction()
                true
            } else {
                false
            }
        }
    }

    private fun sendToPsychApi(text: String) {
        if (!isNetworkAvailable()) {
            addMessage(ChatMessage(
                "It looks like you're offline right now. I can't connect to my server, " +
                "but I want you to know I'm here. Try checking your WiFi or mobile data, " +
                "and we can continue our conversation once you're back online.",
                isUser = false
            ))
            return
        }

        if (!apiAvailable || psychRepository == null) {
            // Try to reconnect in background
            lifecycleScope.launch {
                Log.d(TAG, "API not available, attempting reconnect...")
                initPsychApi()
            }
            addMessage(ChatMessage(
                "I'm connecting to my server... Please try sending your message again in a moment.",
                isUser = false
            ))
            return
        }

        setLoading(true)
        etChatInput.isEnabled = false

        lifecycleScope.launch {
            try {
                when (val result = psychRepository!!.sendMessage(text, psychSessionId)) {
                    is PsychResult.Success -> {
                        val resp = result.data
                        if (resp.sessionId.isNotEmpty()) {
                            psychSessionId = resp.sessionId
                        }

                        val responseText = resp.response.ifEmpty { "I'm processing your message..." }
                        addMessage(ChatMessage(responseText, isUser = false))

                        @Suppress("SENSELESS_COMPARISON")
                        if (resp.safety != null && resp.safety.isCrisis) {
                            Toast.makeText(
                                this@OrynActivity,
                                "Crisis resources are available. Please reach out for help.",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        @Suppress("SENSELESS_COMPARISON")
                        val domain = if (resp.analysis != null) resp.analysis.domain else "unknown"
                        @Suppress("SENSELESS_COMPARISON")
                        val phase = if (resp.analysis != null) resp.analysis.phase else "unknown"
                        @Suppress("SENSELESS_COMPARISON")
                        val latency = if (resp.metrics != null) resp.metrics.latencyMs else 0
                        Log.d(TAG, "Domain: $domain, Phase: $phase, Latency: ${latency}ms")
                    }
                    is PsychResult.Error -> {
                        Log.e(TAG, "API error (code ${result.code}): ${result.message}")

                        // If 401 Unauthorized, try to re-authenticate and retry
                        if (result.code == 401) {
                            Log.d(TAG, "Got 401, attempting re-authentication...")
                            val reAuthOk = autoAuthenticate()
                            if (reAuthOk) {
                                // Retry the message
                                when (val retryResult = psychRepository!!.sendMessage(text, psychSessionId)) {
                                    is PsychResult.Success -> {
                                        val resp = retryResult.data
                                        if (resp.sessionId.isNotEmpty()) psychSessionId = resp.sessionId
                                        addMessage(ChatMessage(resp.response.ifEmpty { "I'm processing..." }, isUser = false))
                                        setLoading(false)
                                        etChatInput.isEnabled = true
                                        return@launch
                                    }
                                    else -> {
                                        Log.e(TAG, "Retry after re-auth also failed")
                                    }
                                }
                            }
                        }

                        addMessage(ChatMessage(
                            "I had trouble processing that. Could you try again? " +
                            "I want to make sure I understand you correctly.",
                            isUser = false
                        ))
                    }
                    is PsychResult.Loading -> { /* Already showing loading */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in sendToPsychApi", e)
                addMessage(ChatMessage(
                    "Something went wrong on my end. Please try again in a moment.",
                    isUser = false
                ))
            }

            setLoading(false)
            etChatInput.isEnabled = true
            etChatInput.requestFocus()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun setLoading(loading: Boolean) {
        progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }
}
