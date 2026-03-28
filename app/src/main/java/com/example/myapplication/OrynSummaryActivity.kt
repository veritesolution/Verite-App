package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.ChatRepository
import com.example.myapplication.ui.components.PieChartView
import com.example.myapplication.util.ProfileIconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrynSummaryActivity : AppCompatActivity() {

    private lateinit var chatRepository: ChatRepository
    private var sessionId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oryn_summary)

        chatRepository = ChatRepository(this)
        sessionId = intent.getLongExtra("SESSION_ID", -1)

        setupUI()
        loadSessionData()
    }

    private fun setupUI() {
        // Header
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val profileIcon = findViewById<ImageView>(R.id.profileIcon)
        profileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Real-time profile icon sync
        ProfileIconHelper.syncProfileIcon(this, profileIcon)

        // Branded header
        val tvAppTitle = findViewById<TextView>(R.id.headerTitle)
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(tvAppTitle)

        // Feedback Button
        findViewById<Button>(R.id.btnFeedbackSettings).setOnClickListener {
            startActivity(Intent(this, FeedbackSettingsActivity::class.java))
        }
    }

    private fun loadSessionData() {
        val tvSummaryBody = findViewById<TextView>(R.id.tvSummarizedBody)
        val tvSummaryTitle = findViewById<TextView>(R.id.tvSummarizedTitle)
        val pieChartView = findViewById<PieChartView>(R.id.pieChartView)

        if (sessionId == -1L) {
            // No specific session — try to load the most recent one
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(this@OrynSummaryActivity)
                val recentSession = withContext(Dispatchers.IO) {
                    db.chatDao().getActiveSession()
                        ?: db.chatDao().getAllSessions().let { flow ->
                            // Get first from flow
                            var result: com.example.myapplication.data.model.ChatSession? = null
                            flow.collect { sessions ->
                                result = sessions.firstOrNull()
                                return@collect
                            }
                            result
                        }
                }
                if (recentSession != null) {
                    sessionId = recentSession.id
                    loadRealSessionData(tvSummaryBody, tvSummaryTitle, pieChartView)
                } else {
                    tvSummaryBody.text = "No conversation sessions found. Start chatting with Oryn to see your session summary here."
                    loadEmptyChart(pieChartView)
                }
            }
        } else {
            loadRealSessionData(tvSummaryBody, tvSummaryTitle, pieChartView)
        }
    }

    private fun loadRealSessionData(
        tvSummaryBody: TextView,
        tvSummaryTitle: TextView,
        pieChartView: PieChartView
    ) {
        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    chatRepository.getSessionById(sessionId)
                }

                if (session == null) {
                    tvSummaryBody.text = "Session not found."
                    loadEmptyChart(pieChartView)
                    return@launch
                }

                // Load summary — generate if not yet available
                tvSummaryTitle.text = "Session Summary"
                if (session.summary != null) {
                    tvSummaryBody.text = session.summary
                } else {
                    tvSummaryBody.text = "Generating AI summary..."
                    val summary = withContext(Dispatchers.IO) {
                        chatRepository.generateSessionSummary(sessionId)
                    }
                    tvSummaryBody.text = summary ?: "No summary available for this session."
                }

                // Load domain distribution for pie chart
                val domains = withContext(Dispatchers.IO) {
                    chatRepository.getDomainDistribution(sessionId)
                }
                val crisisCount = withContext(Dispatchers.IO) {
                    chatRepository.getCrisisMessageCount(sessionId)
                }
                val avgIntensity = withContext(Dispatchers.IO) {
                    chatRepository.getAvgEmotionalIntensity(sessionId)
                }

                // Build pie chart from real domain data
                if (domains.isNotEmpty()) {
                    val domainColors = mapOf(
                        "health" to "#A1BCB9",
                        "anxiety" to "#808483",
                        "depression" to "#1B292C",
                        "relationships" to "#206764",
                        "stress" to "#4A8C85",
                        "trauma" to "#2D4F4A",
                        "grief" to "#5C9E96",
                        "unknown" to "#3A6B66"
                    )

                    val totalCount = domains.sumOf { it.count }.toFloat()
                    val slices = domains.map { domain ->
                        val color = domainColors[domain.domain] ?: "#3A6B66"
                        PieChartView.Slice(
                            value = (domain.count / totalCount) * 100f,
                            color = Color.parseColor(color)
                        )
                    }
                    pieChartView.setSlices(slices)

                    // Set legend keys with real data
                    val keyViews = listOf(
                        findViewById<View>(R.id.key1),
                        findViewById<View>(R.id.key2),
                        findViewById<View>(R.id.key3),
                        findViewById<View>(R.id.key4)
                    )
                    val defaultColors = listOf("#A1BCB9", "#808483", "#1B292C", "#206764")

                    for (i in keyViews.indices) {
                        if (i < domains.size) {
                            val domainName = domains[i].domain.replaceFirstChar { it.uppercase() }
                            val color = domainColors[domains[i].domain] ?: defaultColors[i]
                            setupKey(keyViews[i], Color.parseColor(color), domainName)
                            keyViews[i].visibility = View.VISIBLE
                        } else {
                            keyViews[i].visibility = View.GONE
                        }
                    }
                } else {
                    loadEmptyChart(pieChartView)
                    setupDefaultKeys()
                }

            } catch (e: Exception) {
                tvSummaryBody.text = "Error loading session data: ${e.message}"
                loadEmptyChart(pieChartView)
            }
        }
    }

    private fun setupDefaultKeys() {
        setupKey(findViewById(R.id.key1), Color.parseColor("#A1BCB9"), "Health")
        setupKey(findViewById(R.id.key2), Color.parseColor("#808483"), "Anxiety")
        setupKey(findViewById(R.id.key3), Color.parseColor("#1B292C"), "Stress")
        setupKey(findViewById(R.id.key4), Color.parseColor("#206764"), "Other")
    }

    private fun loadEmptyChart(pieChartView: PieChartView) {
        val slices = listOf(
            PieChartView.Slice(value = 100f, color = Color.parseColor("#1B292C"))
        )
        pieChartView.setSlices(slices)
        setupDefaultKeys()
    }

    private fun setupKey(keyView: View, colorInt: Int, label: String) {
        val colorDot = keyView.findViewById<View>(R.id.colorDot)
        val tvLabel = keyView.findViewById<TextView>(R.id.tvKeyName)

        colorDot.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
        tvLabel.text = label
    }
}
