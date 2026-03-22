package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme

class AilmentCategoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ailment_category)

        findViewById<ComposeView>(R.id.composeView).setContent {
            VeriteTheme {
                SkyBackground { }
            }
        }

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<android.view.View>(R.id.profileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
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
            val intent = Intent(this, AilmentDetailActivity::class.java)
            intent.putExtra("AILMENT_TYPE", category)
            startActivity(intent)
        }
    }
}
