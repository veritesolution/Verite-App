package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportAdapter(
    private val reports: List<File>,
    private val onReportClick: (File) -> Unit
) : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {

    class ReportViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.reportName)
        val dateText: TextView = view.findViewById(R.id.reportDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        holder.nameText.text = report.name
        
        val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            .format(Date(report.lastModified()))
        holder.dateText.text = date
        
        holder.itemView.setOnClickListener { onReportClick(report) }
    }

    override fun getItemCount() = reports.size
}
