package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.util.ProfileIconHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OrynActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var etChatInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oryn)

        val database = AppDatabase.getDatabase(this)

        setupUI(database)
        setupChat(database)
        
        // Initial Oryn Greeting
        simulateOrynResponse("Hello! I am Oryn. How can I assist you with your mental clarity today?")
    }

    private fun setupUI(database: AppDatabase) {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val profileIcon = findViewById<ImageView>(R.id.profileIcon)
        profileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        ProfileIconHelper.syncProfileIcon(this, profileIcon)

        // Branded header logo
        val headerTitle = findViewById<android.widget.TextView>(R.id.headerTitle)
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(headerTitle)

        findViewById<Button>(R.id.btnCheckSummary).setOnClickListener {
            startActivity(Intent(this, OrynSummaryActivity::class.java))
        }
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
                
                // Simulate Oryn typing and replying
                lifecycleScope.launch {
                    delay(1000) // 1 second delay
                    simulateOrynResponse("That's interesting. Tell me more about how that made you feel.")
                }
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

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    private fun simulateOrynResponse(text: String) {
        addMessage(ChatMessage(text, isUser = false))
    }
}
