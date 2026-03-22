package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class AudioSearchActivity : AppCompatActivity() {

    private lateinit var rvSearchResults: RecyclerView
    private lateinit var etSearchInput: EditText
    private lateinit var btnClear: ImageView
    private lateinit var tvResultsCount: TextView
    private lateinit var emptyStateView: View

    private lateinit var chipAll: TextView
    private lateinit var chipFocus: TextView
    private lateinit var chipRelax: TextView
    private lateinit var chipSleep: TextView
    private lateinit var chipMeditate: TextView

    private val allSoundscapes = listOf(
        Soundscape("sunset", "Focus Sunset", R.drawable.sc_sunset),
        Soundscape("camp", "Relaxing Camp", R.drawable.sc_camp),
        Soundscape("sunrise", "Morning Sunrise", R.drawable.sc_sunrise),
        Soundscape("sunset2", "Deep Sleep Waves", R.drawable.sc_sunset),
        Soundscape("sunrise2", "Meditative Morning", R.drawable.sc_sunrise),
        Soundscape("sunrise3", "Focus River", R.drawable.sc_sunrise)
    )

    private var currentCategory = "All"
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_search)

        // Branded header
        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(headerTitle)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.profileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        etSearchInput = findViewById(R.id.etSearchInput)
        btnClear = findViewById(R.id.btnClear)
        rvSearchResults = findViewById(R.id.rvSearchResults)
        tvResultsCount = findViewById(R.id.tvResultsCount)
        emptyStateView = findViewById(R.id.emptyStateView)

        chipAll = findViewById(R.id.chipAll)
        chipFocus = findViewById(R.id.chipFocus)
        chipRelax = findViewById(R.id.chipRelax)
        chipSleep = findViewById(R.id.chipSleep)
        chipMeditate = findViewById(R.id.chipMeditate)

        setupSearch()
        setupChips()
        filterResults()
    }

    private fun setupSearch() {
        etSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                btnClear.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterResults()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClear.setOnClickListener {
            etSearchInput.text.clear()
        }
    }

    private fun setupChips() {
        val chips = mapOf(
            "All" to chipAll,
            "Focus" to chipFocus,
            "Relax" to chipRelax,
            "Sleep" to chipSleep,
            "Meditate" to chipMeditate
        )

        chips.forEach { (category, textView) ->
            textView.setOnClickListener {
                currentCategory = category
                updateChipUI()
                filterResults()
            }
        }
    }

    private fun updateChipUI() {
        val activeBg = R.drawable.pill_bg
        val inactiveBg = R.drawable.pill_bg

        // Reset all
        listOf(chipAll, chipFocus, chipRelax, chipSleep, chipMeditate).forEach {
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0C1917"))
            it.setTextColor(Color.parseColor("#A0FFFFFF"))
        }

        // Set active
        val activeChip = when (currentCategory) {
            "All" -> chipAll
            "Focus" -> chipFocus
            "Relax" -> chipRelax
            "Sleep" -> chipSleep
            "Meditate" -> chipMeditate
            else -> chipAll
        }

        activeChip.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5"))
        activeChip.setTextColor(Color.parseColor("#000000"))
    }

    private fun filterResults() {
        val filtered = allSoundscapes.filter { soundscape ->
            val matchesSearch = soundscape.title.contains(searchQuery, ignoreCase = true)
            val matchesCategory = if (currentCategory == "All") true else soundscape.title.contains(currentCategory, ignoreCase = true)
            matchesSearch && matchesCategory
        }

        tvResultsCount.text = "${filtered.size} results"

        if (filtered.isEmpty()) {
            rvSearchResults.visibility = View.GONE
            emptyStateView.visibility = View.VISIBLE
        } else {
            rvSearchResults.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
            rvSearchResults.adapter = SearchResultAdapter(filtered) { startSession(it) }
        }
    }

    private fun startSession(soundscape: Soundscape) {
        val intent = Intent(this, SoundExplorerActivity::class.java)
        // Pass "All" or a more specific category if needed
        val cat = if (currentCategory == "All") "Focus" else currentCategory
        intent.putExtra("CATEGORY", cat)
        startActivity(intent)
    }

    inner class SearchResultAdapter(private val items: List<Soundscape>, private val onClick: (Soundscape) -> Unit) :
        RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_soundscape_card, parent, false)
            // Adjust width for list view
            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.setMargins(0, 0, 0, 16)
            view.layoutParams = lp
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.thumbnail.setImageResource(item.thumbnailRes)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvTitle)
            val thumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        }
    }
}
