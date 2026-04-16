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
import com.example.myapplication.ui.components.VeriteAlert

class DailyProgressActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val planId = intent.getLongExtra("PLAN_ID", -1L)
        if (planId == -1L) {
            VeriteAlert.error(this, "Error: No plan ID provided")
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
                                ailmentType = plan.ailmentType,
                                currentDay = plan.currentDay,
                                dailyFocusMinutes = plan.dailyFocusMinutes,
                                completedFocusMinutes = plan.completedFocusMinutes,
                                aiSuggestion = TextCleaningUtils.extractSuggestion(
                                    plan.fullPlanText,
                                    plan.currentDay
                                ),
                                onBackClick = { finish() },
                                onProfileClick = {
                                    startActivity(android.content.Intent(this@DailyProgressActivity, ProfileActivity::class.java))
                                },
                                onViewTaskClick = {
                                    VeriteAlert.info(this@DailyProgressActivity, "Detailed tasks coming soon!")
                                },
                                onStartFocusSession = {
                                    VeriteAlert.info(this@DailyProgressActivity, "Focus session starting...")
                                }
                            )
                        }
                    }
                } else {
                    VeriteAlert.error(this@DailyProgressActivity, "Error: Plan found in intent but not in DB (ID: $planId)")
                    finish()
                }
            } catch (e: Exception) {
                VeriteAlert.error(this@DailyProgressActivity, "Error loading plan: ${e.message}")
                e.printStackTrace()
                finish()
            }
        }
    }
}
