package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

data class Soundscape(val id: String, val title: String, val thumbnailRes: Int)

class SoundExplorerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound_explorer)

        val category = intent.getStringExtra("CATEGORY") ?: "Focus"
        findViewById<TextView>(R.id.categoryTitle).text = category

        // Branded header
        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        headerTitle?.let {
            com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(it)
        }

        findViewById<android.view.View>(R.id.backButton)?.setOnClickListener { finish() }

        val soundscapes = listOf(
            Soundscape("sunset", "sunset", R.drawable.sc_sunset),
            Soundscape("camp", "Camp", R.drawable.sc_camp),
            Soundscape("sunrise", "sunrise", R.drawable.sc_sunrise),
            Soundscape("sunset2", "sunset", R.drawable.sc_sunset),
            Soundscape("sunrise2", "sunrise", R.drawable.sc_sunrise),
            Soundscape("sunrise3", "sunrise", R.drawable.sc_sunrise)
        )

        val recentAudios = soundscapes.take(3)
        val rvRecent = findViewById<RecyclerView>(R.id.rvRecentAudios)
        rvRecent.adapter = SoundscapeAdapter(recentAudios) { startSession(category, it) }

        val rvExplore = findViewById<RecyclerView>(R.id.rvExploreAudios)
        rvExplore.adapter = SoundscapeAdapter(soundscapes) { startSession(category, it) }
    }

    private fun startSession(category: String, soundscape: Soundscape) {
        val targetActivity = when (category) {
            "Focus" -> FocusSoundActivity::class.java
            "Relax" -> RelaxSoundActivity::class.java
            "Sleep" -> SleepSoundActivity::class.java
            "Meditate" -> MeditateActivity::class.java
            else -> FocusSoundActivity::class.java
        }
        val intent = Intent(this, targetActivity)
        intent.putExtra("SOUNDSCAPE_TITLE", soundscape.title)
        intent.putExtra("SOUNDSCAPE_ID", soundscape.id)
        startActivity(intent)
    }

    inner class SoundscapeAdapter(
        private val items: List<Soundscape>,
        private val onItemClick: (Soundscape) -> Unit
    ) : RecyclerView.Adapter<SoundscapeAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = if (parent.id == R.id.rvRecentAudios) R.layout.item_soundscape_card else R.layout.item_soundscape_grid
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.thumbnail.setImageResource(item.thumbnailRes)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvTitle)
            val thumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        }
    }
}
