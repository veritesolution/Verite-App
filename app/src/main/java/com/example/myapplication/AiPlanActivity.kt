package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ui.components.VeriteAlert
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.RecoveryRepository
import com.example.myapplication.viewmodel.AiPlanState
import com.example.myapplication.viewmodel.AiViewModel
import com.example.myapplication.viewmodel.AiViewModelFactory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AiPlanActivity : AppCompatActivity() {

    private var currentPlanId: Long = -1L

    private val viewModel: AiViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val recoveryRepository = RecoveryRepository(database.recoveryPlanDao())
        val aiRepository = AiRepository()
        AiViewModelFactory(aiRepository, recoveryRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_ai_plan)

            findViewById<ComposeView>(R.id.composeView).setContent {
                VeriteTheme {
                    SkyBackground { }
                }
            }

            val ailmentType = intent.getStringExtra("AILMENT_TYPE") ?: ""
            val frequency = intent.getStringExtra("FREQUENCY") ?: ""
            val duration = intent.getStringExtra("DURATION") ?: ""
            val trigger = intent.getStringExtra("TRIGGER") ?: ""
            val motivation = intent.getStringExtra("MOTIVATION") ?: ""

            android.util.Log.d("AiPlanActivity", "Starting generation for: $ailmentType")

            val loadingSpinner = findViewById<ProgressBar>(R.id.loadingSpinner)
            val loadingText = findViewById<TextView>(R.id.loadingText)
            val resultScrollView = findViewById<View>(R.id.resultScrollView)
            val planContent = findViewById<TextView>(R.id.planContent)
            val btnSave = findViewById<Button>(R.id.btnSave)
            val btnRetry = findViewById<Button>(R.id.btnRetry)

            findViewById<View>(R.id.backButton).setOnClickListener { finish() }

            findViewById<View>(R.id.profileIcon).setOnClickListener {
                startActivity(Intent(this, ProfileActivity::class.java))
            }

            btnRetry.setOnClickListener {
                btnRetry.visibility = View.GONE
                loadingText.text = "Generating your personalized plan..."
                loadingText.setTextColor(android.graphics.Color.parseColor("#888888"))
                viewModel.generatePlan(ailmentType, frequency, trigger, duration, motivation)
            }

            // Start Generation
            if (savedInstanceState == null) {
                viewModel.generatePlan(ailmentType, frequency, trigger, duration, motivation)
            }

            lifecycleScope.launch {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AiPlanState.Idle -> { /* Do nothing */
                        }

                        is AiPlanState.Loading -> {
                            loadingSpinner.visibility = View.VISIBLE
                            loadingText.visibility = View.VISIBLE
                            loadingText.text = "Generating your personalized plan..."
                            loadingText.setTextColor(android.graphics.Color.parseColor("#888888"))
                            resultScrollView.visibility = View.GONE
                            btnSave.visibility = View.GONE
                            btnRetry.visibility = View.GONE
                        }

                        is AiPlanState.Success -> {
                            loadingSpinner.visibility = View.GONE
                            loadingText.visibility = View.GONE
                            resultScrollView.visibility = View.VISIBLE
                            btnSave.visibility = View.VISIBLE
                            btnRetry.visibility = View.GONE
                            planContent.text = state.plan
                            currentPlanId = state.planId
                        }

                        is AiPlanState.Error -> {
                            loadingSpinner.visibility = View.GONE
                            loadingText.visibility = View.VISIBLE
                            loadingText.text = state.message
                            loadingText.setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
                            btnRetry.visibility = View.VISIBLE
                            android.util.Log.e("AiPlanActivity", "UI Error State: ${state.message}")
                        }
                    }
                }
            }

            btnSave.setOnClickListener {
                if (currentPlanId != -1L) {
                    val intent = Intent(this@AiPlanActivity, AiAgreementActivity::class.java)
                    intent.putExtra("PLAN_ID", currentPlanId)
                    startActivity(intent)
                } else {
                    lifecycleScope.launch {
                        try {
                            val currentState = viewModel.uiState.value
                            if (currentState is AiPlanState.Success) {
                                // Save the plan to database
                                val database = AppDatabase.getDatabase(applicationContext)
                                val plan = com.example.myapplication.data.model.RecoveryPlan(
                                    ailmentType = ailmentType,
                                    fullPlanText = currentState.plan,
                                    frequency = frequency,
                                    reasonForAilment = trigger,
                                    duration = duration,
                                    reasonForStopping = motivation
                                )
                                val planId = database.recoveryPlanDao().insertPlan(plan)
                                currentPlanId = planId

                                // Navigate to agreement with plan ID
                                val intent =
                                    Intent(this@AiPlanActivity, AiAgreementActivity::class.java)
                                intent.putExtra("PLAN_ID", currentPlanId)
                                startActivity(intent)
                            } else {
                                VeriteAlert.info(
                                    this@AiPlanActivity,
                                    "Please wait for plan to save..."
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AiPlanActivity", "Error saving plan", e)
                            VeriteAlert.error(
                                this@AiPlanActivity,
                                "Failed to save plan: ${e.message}"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AiPlanActivity", "Crash in onCreate", e)
            VeriteAlert.error(this, "Error initializing screen: ${e.message}")
            finish()
        }
    }
}
