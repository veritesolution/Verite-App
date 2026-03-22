package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.DreamEntry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DreamJournalActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DreamAdapter
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dream_journal)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.dreamRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DreamAdapter(emptyList(), { dream -> deleteDream(dream) })
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            database.dreamDao().getAllDreams().collect { dreams ->
                adapter.updateList(dreams)
            }
        }

        findViewById<View>(R.id.fabAddDream).setOnClickListener {
            showAddDreamDialog()
        }
    }

    /* add showadddream dialog */

    private fun showAddDreamDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_dream, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etDreamTitle)
        val etContent = dialogView.findViewById<EditText>(R.id.etDreamContent)
        val spinnerMood = dialogView.findViewById<Spinner>(R.id.spinnerMood)

        val moods = arrayOf("Peaceful", "Neutral", "Intense", "Nightmare", "Lucid")
        spinnerMood.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, moods)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Record Dream")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = etTitle.text.toString()
                val content = etContent.text.toString()
                val mood = spinnerMood.selectedItem.toString()

                if (title.isNotEmpty() && content.isNotEmpty()) {
                    lifecycleScope.launch {
                        database.dreamDao().insertDream(DreamEntry(title = title, content = content, mood = mood))
                    }
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDream(dream: DreamEntry) {
        lifecycleScope.launch {
            database.dreamDao().deleteDream(dream)
        }
    }

    /*add a class */

    class DreamAdapter(
        private var list: List<DreamEntry>,
        private val onDelete: (DreamEntry) -> Unit
    ) : RecyclerView.Adapter<DreamAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvDreamTitle)
            val tvMood: TextView = v.findViewById(R.id.tvDreamMood)
            val tvContent: TextView = v.findViewById(R.id.tvDreamContent)
            val tvDate: TextView = v.findViewById(R.id.tvDreamDate)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dream, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val dream = list[position]
            holder.tvTitle.text = dream.title
            holder.tvMood.text = dream.mood
            holder.tvContent.text = dream.content
            
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(dream.timestamp))
            
            holder.btnDelete.setOnClickListener { onDelete(dream) }
        }

        override fun getItemCount() = list.size

        fun updateList(newList: List<DreamEntry>) {
            list = newList
            notifyDataSetChanged()
        }
    }
}
