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
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.RecoveryRepository
import com.example.myapplication.viewmodel.AiPlanState
import com.example.myapplication.viewmodel.AiViewModel
import com.example.myapplication.viewmodel.AiViewModelFactory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AiPlanActivity : AppCompatActivity() {

    private val viewModel: AiViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val recoveryRepository = RecoveryRepository(database.recoveryPlanDao())
        val aiRepository = AiRepository()
        AiViewModelFactory(aiRepository, recoveryRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_plan)

        val addictionType = intent.getStringExtra("ADDICTION_TYPE") ?: ""
        val frequency = intent.getStringExtra("FREQUENCY") ?: ""
        val duration = intent.getStringExtra("DURATION") ?: ""
        val trigger = intent.getStringExtra("TRIGGER") ?: ""
        val motivation = intent.getStringExtra("MOTIVATION") ?: ""

        val loadingSpinner = findViewById<ProgressBar>(R.id.loadingSpinner)
        val loadingText = findViewById<TextView>(R.id.loadingText)
        val resultScrollView = findViewById<View>(R.id.resultScrollView)
        val planContent = findViewById<TextView>(R.id.planContent)
        val btnSave = findViewById<Button>(R.id.btnSave)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        // Start Generation
        if (savedInstanceState == null) {
            viewModel.generatePlan(addictionType, frequency, trigger, duration, motivation)
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is AiPlanState.Idle -> { /* Do nothing */ }
                    is AiPlanState.Loading -> {
                        loadingSpinner.visibility = View.VISIBLE
                        loadingText.visibility = View.VISIBLE
                        resultScrollView.visibility = View.GONE
                        btnSave.visibility = View.GONE
                    }
                    is AiPlanState.Success -> {
                        loadingSpinner.visibility = View.GONE
                        loadingText.visibility = View.GONE
                        resultScrollView.visibility = View.VISIBLE
                        btnSave.visibility = View.VISIBLE
                        planContent.text = state.plan
                    }
                    is AiPlanState.Error -> {
                        loadingSpinner.visibility = View.GONE
                        loadingText.visibility = View.VISIBLE
                        loadingText.text = "Error: ${state.message}"
                        loadingText.setTextColor(android.graphics.Color.RED)
                        Toast.makeText(this@AiPlanActivity, "Plan generation failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnSave.setOnClickListener {
            val intent = Intent(this, AiAgreementActivity::class.java)
            startActivity(intent)
        }
    }
}
