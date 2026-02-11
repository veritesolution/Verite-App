package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.RecoveryRepository
import com.example.myapplication.viewmodel.DashboardViewModel
import com.example.myapplication.viewmodel.DashboardViewModelFactory
import kotlinx.coroutines.launch

class LearningSessionActivity : AppCompatActivity() {

    private lateinit var viewModel: DashboardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_session)

        val database = AppDatabase.getDatabase(this)
        val repository = RecoveryRepository(database.recoveryPlanDao())
        val factory = DashboardViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnStartSession).setOnClickListener {
            // Navigate to Addiction Flow if no plan, or resume session
            // For now, redirect to create plan
            startActivity(Intent(this, AddictionCategoryActivity::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
    }

    private fun updateUI(state: com.example.myapplication.viewmodel.DashboardUiState) {
        val tvActivePlan = findViewById<TextView>(R.id.tvActivePlan)
        val tvDay = findViewById<TextView>(R.id.tvDay)
        val tvTodaysFocus = findViewById<TextView>(R.id.tvTodaysFocus)
        val tvTodaysAction = findViewById<TextView>(R.id.tvTodaysAction)
        val tvMotivation = findViewById<TextView>(R.id.tvMotivation)
        val btnStartSession = findViewById<Button>(R.id.btnStartSession)
        val tipsContainer = findViewById<LinearLayout>(R.id.tipsContainer)
        val tvTipsTitle = findViewById<TextView>(R.id.tvTipsTitle)
        val progressChartView = findViewById<com.example.myapplication.ui.components.ProgressChartView>(R.id.progressChartView)

        progressChartView.setProgressData(state.weeklyProgress)

        if (state.activePlan != null) {
            tvActivePlan.text = state.activePlan.addictionType
            tvDay.text = "Day ${state.currentDay}"
            btnStartSession.text = "Resume Focus Session"
        } else {
            tvActivePlan.text = "No Active Plan"
            tvDay.text = "Get Started"
            btnStartSession.text = "Plan Your Recovery"
        }

        tvTodaysFocus.text = state.todaysFocus
        tvTodaysAction.text = state.todaysAction
        tvMotivation.text = "\"${state.todaysMotivation}\""

        // Update tips
        tipsContainer.removeAllViews()
        if (state.generalTips.isNotEmpty()) {
            tvTipsTitle.visibility = View.VISIBLE
            state.generalTips.forEach { tip ->
                val tipView = TextView(this).apply {
                    text = "• $tip"
                    setTextColor(resources.getColor(android.R.color.darker_gray, null))
                    setPadding(16, 8, 16, 8)
                }
                tipsContainer.addView(tipView)
            }
        } else {
            tvTipsTitle.visibility = View.GONE
        }
    }
}
