package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.utils.ReportUtils

class SavedReportsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_reports)

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        val emptyView = findViewById<TextView>(R.id.emptyView)
        val recyclerView = findViewById<RecyclerView>(R.id.reportsRecyclerView)

        val reports = ReportUtils.listSavedReports(this)

        if (reports.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            
            recyclerView.adapter = ReportAdapter(reports) { report ->
                val intent = Intent(this, ReportDetailActivity::class.java).apply {
                    putExtra("REPORT_FILE_NAME", report.name)
                }
                startActivity(intent)
            }
        }
    }
}
