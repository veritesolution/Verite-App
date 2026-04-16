package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.ChatMessageEntity
import com.example.myapplication.ui.chat.OrynChatViewModel
import com.example.myapplication.util.ProfileIconHelper
import coil.load
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.example.myapplication.ui.components.VeriteAlert

class OrynActivity : AppCompatActivity() {

    private val viewModel: OrynChatViewModel by viewModels()

    private lateinit var adapter: OrynChatAdapter
    private val messages = mutableListOf<ChatMessageEntity>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var etChatInput: EditText
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oryn)

        val database = AppDatabase.getDatabase(this)

        setupUI(database)
        setupChat(database)
        observeViewModel()

        // Add greeting if this is a fresh session (no messages yet)
        // The ViewModel will load existing messages from DB
    }

    private fun setupUI(database: AppDatabase) {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val profileIcon = findViewById<ImageView>(R.id.profileIcon)
        profileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Real-time profile icon sync using Flow (updates when image changes)
        ProfileIconHelper.syncProfileIcon(this, profileIcon)

        val headerTitle = findViewById<android.widget.TextView>(R.id.headerTitle)
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(headerTitle)

        findViewById<Button>(R.id.btnCheckSummary).setOnClickListener {
            // End session + generate summary, then navigate
            viewModel.endSessionAndSummarize { sessionId ->
                val intent = Intent(this, OrynSummaryActivity::class.java)
                if (sessionId != null) {
                    intent.putExtra("SESSION_ID", sessionId)
                }
                startActivity(intent)
            }
        }

        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupChat(database: AppDatabase) {
        recyclerView = findViewById(R.id.chatRecyclerView)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager

        adapter = OrynChatAdapter(messages, database)
        recyclerView.adapter = adapter

        etChatInput = findViewById(R.id.etChatInput)
        val btnSend = findViewById<ImageView>(R.id.btnSend)

        val sendMessageAction = {
            val text = etChatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                etChatInput.text.clear()
                viewModel.sendMessage(text)
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

        // Ensure EditText can receive focus and show keyboard when tapped
        etChatInput.setOnClickListener {
            etChatInput.isFocusableInTouchMode = true
            etChatInput.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etChatInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Observe ViewModel state and update UI reactively.
     * Uses repeatOnLifecycle for proper lifecycle-aware collection.
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Update messages list
                    val hadMessages = messages.isNotEmpty()
                    messages.clear()
                    messages.addAll(state.messages)
                    adapter.notifyDataSetChanged()

                    // Auto-scroll to bottom when new messages arrive
                    if (messages.isNotEmpty()) {
                        recyclerView.scrollToPosition(messages.size - 1)
                    }

                    // Show greeting if first load with no messages
                    if (!hadMessages && state.messages.isEmpty() && !state.isConnecting) {
                        // Add initial greeting (not persisted — just UI decoration)
                    }

                    // Loading state — keep input ALWAYS enabled for typing
                    // Only show progress indicator while loading/connecting
                    progressBar?.visibility = if (state.isLoading || state.isConnecting) View.VISIBLE else View.GONE
                    // Never disable the EditText — users should always be able to type
                    etChatInput.isEnabled = true

                    // Update hint to show connection status
                    if (state.isConnecting) {
                        etChatInput.hint = "Connecting to Oryn..."
                    } else if (state.isLoading) {
                        etChatInput.hint = "Oryn is thinking..."
                    } else {
                        etChatInput.hint = "Write your message"
                    }

                    // Crisis alert
                    if (state.isCrisisActive) {
                        VeriteAlert.warning(
                            this@OrynActivity,
                            "Crisis resources are available. Please reach out for help."
                        )
                    }

                    // Error handling
                    state.error?.let { error ->
                        VeriteAlert.error(this@OrynActivity, error)
                        viewModel.clearError()
                    }
                }
            }
        }
    }
}

/**
 * Upgraded ChatAdapter that works with ChatMessageEntity from Room DB.
 * Supports persistent messages with rich metadata.
 */
class OrynChatAdapter(
    private val messages: List<ChatMessageEntity>,
    private val database: AppDatabase
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ORYN = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ORYN
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = android.view.LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_oryn, parent, false)
            OrynViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserViewHolder) holder.bind(message)
        else if (holder is OrynViewHolder) holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: android.widget.TextView = itemView.findViewById(R.id.tvUserMessage)
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivUserAvatar)
        private var avatarJob: kotlinx.coroutines.Job? = null

        fun bind(message: ChatMessageEntity) {
            tvMessage.text = message.content

            avatarJob?.cancel()
            avatarJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val user = database.userDao().getUser().firstOrNull()
                    user?.profileImagePath?.let { path ->
                        val file = java.io.File(path)
                        if (file.exists()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                ivAvatar.clearColorFilter()
                                ivAvatar.load(file) {
                                    transformations(coil.transform.CircleCropTransformation())
                                }
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    inner class OrynViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: android.widget.TextView = itemView.findViewById(R.id.tvOrynMessage)

        fun bind(message: ChatMessageEntity) {
            tvMessage.text = message.content
        }
    }
}
