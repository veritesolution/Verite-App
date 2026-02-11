package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AiAgreementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_agreement)

        findViewById<Button>(R.id.btnAccept).setOnClickListener {
            Toast.makeText(this, "Plan Accepted! Good luck on your journey.", Toast.LENGTH_LONG).show()
            
            val planId = intent.getLongExtra("PLAN_ID", -1L)
            
            val intent = Intent(this, DailyProgressActivity::class.java)
            intent.putExtra("PLAN_ID", planId)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish() // Close Agreement activity so back button doesn't return here
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            finish()
        }
    }
}
