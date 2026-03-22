package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme

class AiAgreementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_agreement)

        findViewById<ComposeView>(R.id.composeView).setContent {
            VeriteTheme {
                SkyBackground { }
            }
        }

        findViewById<Button>(R.id.btnAccept).setOnClickListener {
            Toast.makeText(this, "Plan Accepted! Good luck on your journey.", Toast.LENGTH_LONG).show()
                        // Get the plan ID from intent (should be passed from AiPlanActivity)
            val planId = intent.getLongExtra("PLAN_ID", -1L)
            
            // Navigate to Daily Progress screen
            val progressIntent = Intent(this, DailyProgressActivity::class.java)
            progressIntent.putExtra("PLAN_ID", planId)
            progressIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(progressIntent)

            finish() // Close Agreement activity so back button doesn't return here
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            finish()
        }
    }
}
