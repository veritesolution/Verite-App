package com.example.myapplication

import android.content.Intent
import android.os.Bundle
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

    private fun initPsychApi() {
        try {
            PsychNetworkModule.init(this)
            psychRepository = PsychRepository()
            apiAvailable = true

            // Check server health in background
            lifecycleScope.launch {
                when (val result = psychRepository?.healthCheck()) {
                    is PsychResult.Success -> {
                        Log.i(TAG, "Psychologist API online: ${result.data.version}")
                        apiAvailable = true
                    }
                    is PsychResult.Error -> {
                        Log.w(TAG, "Psychologist API unavailable: ${result.message}")
                        apiAvailable = false
                    }
                    else -> { apiAvailable = false }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize Psychologist API", e)
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
        if (!apiAvailable || psychRepository == null) {
            // Fallback to local response when API is unavailable
            addMessage(ChatMessage(
                "I'm currently in offline mode. Please check your connection to the Verite server. " +
                "In the meantime, I'm here to listen. Could you tell me more about what's on your mind?",
                isUser = false
            ))
            return
        }

        setLoading(true)
        etChatInput.isEnabled = false

        lifecycleScope.launch {
            when (val result = psychRepository!!.sendMessage(text, psychSessionId)) {
                is PsychResult.Success -> {
                    val resp = result.data
                    if (resp.sessionId.isNotEmpty()) {
                        psychSessionId = resp.sessionId
                    }

                    val responseText = resp.response.ifEmpty { "I'm processing your message..." }
                    addMessage(ChatMessage(responseText, isUser = false))

                    // Check for crisis (safe access — Gson may deliver null despite non-null type)
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
                    Log.e(TAG, "API error: ${result.message}")
                    // Graceful fallback
                    addMessage(ChatMessage(
                        "I had trouble processing that. Could you try again? " +
                        "I want to make sure I understand you correctly.",
                        isUser = false
                    ))
                }
                is PsychResult.Loading -> { /* Already showing loading */ }
            }

            setLoading(false)
            etChatInput.isEnabled = true
            etChatInput.requestFocus()
        }
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
