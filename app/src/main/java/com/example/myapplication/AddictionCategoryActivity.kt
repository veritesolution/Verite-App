package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AddictionCategoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addiction_category)

        findViewById<android.view.View>(R.id.backButton).setOnClickListener {
            finish()
        }

        setupCategoryButton(R.id.btnSmoking, "Smoking")
        setupCategoryButton(R.id.btnAlcohol, "Alcohol")
        setupCategoryButton(R.id.btnSocialMedia, "Social Media")
        setupCategoryButton(R.id.btnGaming, "Gaming")
        setupCategoryButton(R.id.btnJunkFood, "Junk Food")
        setupCategoryButton(R.id.btnPornography, "Pornography")
        setupCategoryButton(R.id.btnProcrastination, "Procrastination")
        setupCategoryButton(R.id.btnOther, "Other")
    }

    private fun setupCategoryButton(buttonId: Int, category: String) {
        findViewById<Button>(buttonId).setOnClickListener {
            val intent = Intent(this, AddictionDetailActivity::class.java)
            intent.putExtra("ADDICTION_TYPE", category)
            startActivity(intent)
        }
    }
}
