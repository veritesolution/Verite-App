package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.utils.ReportUtils
import java.io.File

class ReportDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_detail)

        findViewById<ComposeView>(R.id.composeView).setContent {
            VeriteTheme {
                SkyBackground { }
            }
        }

        val reportFileName = intent.getStringExtra("REPORT_FILE_NAME")

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.profileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<Button>(R.id.btnClose).setOnClickListener {
            finish()
        }

        val contentTextView = findViewById<TextView>(R.id.reportContentText)

        if (reportFileName != null) {
            val directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
            val file = File(directory, reportFileName)
            if (file.exists()) {
                val content = ReportUtils.readReportContent(file)
                contentTextView.text = content
            } else {
                contentTextView.text = "Error: Report file not found."
            }
        } else {
            contentTextView.text = "Error: No report selected."
        }
    }
}
