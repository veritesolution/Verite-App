package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.ui.screens.DailyProgressScreen
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.utils.TextCleaningUtils
import kotlinx.coroutines.launch

class DailyProgressActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val planId = intent.getLongExtra("PLAN_ID", -1L)
<<<<<<< Updated upstream
        if (planId == -1L) {
            Toast.makeText(this, "Error: No plan ID provided", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val database = AppDatabase.getDatabase(applicationContext)
                val plan = database.recoveryPlanDao().getPlanById(planId)

                if (plan != null) {
                    setContent {
                        VeriteTheme {
                            DailyProgressScreen(
                                addictionType = plan.addictionType,
                                currentDay = plan.currentDay,
                                dailyFocusMinutes = plan.dailyFocusMinutes,
                                completedFocusMinutes = plan.completedFocusMinutes,
                                aiSuggestion = TextCleaningUtils.extractSuggestion(
                                    plan.fullPlanText,
                                    plan.currentDay
                                ),
                                onBackClick = { finish() },
                                onViewTaskClick = {
                                    Toast.makeText(this@DailyProgressActivity, "Detailed tasks coming soon!", Toast.LENGTH_SHORT).show()
                                },
                                onStartFocusSession = {
                                    Toast.makeText(this@DailyProgressActivity, "Focus session starting...", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                } else {
                    Toast.makeText(this@DailyProgressActivity, "Error: Plan found in intent but not in DB (ID: $planId)", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DailyProgressActivity, "Error loading plan: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
=======

        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(applicationContext)
            val plan = database.recoveryPlanDao().getPlanById(planId)

            if (plan != null) {
                setContent {
                    VeriteTheme {
                        DailyProgressScreen(
                            addictionType = plan.addictionType,
                            currentDay = plan.currentDay,
                            dailyFocusMinutes = plan.dailyFocusMinutes,
                            completedFocusMinutes = plan.completedFocusMinutes,
                            aiSuggestion = TextCleaningUtils.extractSuggestion(
                                plan.fullPlanText,
                                plan.currentDay
                            ),
                            onBackClick = { finish() },
                            onViewTaskClick = {
                                // TODO: Show detailed task view
                                Toast.makeText(
                                    this@DailyProgressActivity,
                                    "View Task clicked",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onStartFocusSession = {
                                // TODO: Start focus session timer
                                Toast.makeText(
                                    this@DailyProgressActivity,
                                    "Starting Focus Session...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            } else {
                Toast.makeText(this@DailyProgressActivity, "Plan not found", Toast.LENGTH_SHORT).show()
>>>>>>> Stashed changes
                finish()
            }
        }
    }
}
