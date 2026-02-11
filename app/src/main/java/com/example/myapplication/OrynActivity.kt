package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class OrynActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oryn)

        // Back Button
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Action Button
        findViewById<View>(R.id.btnAction).setOnClickListener {
            Toast.makeText(this, "Starting Session with Oryn...", Toast.LENGTH_SHORT).show()
            // Logic to start chat or session would go here
        }
    }
}
