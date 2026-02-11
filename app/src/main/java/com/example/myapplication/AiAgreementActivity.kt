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
            // In a real app, we'd set an "Active Plan" flag in DB/Prefs
            val intent = Intent(this, HeadbandHomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            finish()
        }
    }
}
