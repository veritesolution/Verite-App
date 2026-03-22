package com.example.myapplication

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme

class CustomSoundActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---------- Root FrameLayout to host Background + Content ----------
        val rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ---------- Compose Background ----------
        val composeBackground = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setContent {
                VeriteTheme {
                    SkyBackground { }
                }
            }
        }
        rootFrame.addView(composeBackground)

        // ---------- Content ScrollView ----------
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(20), dpToPx(32), dpToPx(20), dpToPx(40))
        }
        scrollView.addView(root)

        root.addView(ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setOnClickListener { finish() }
        })

        root.addView(TextView(this).apply {
            text = "🎵  Custom Sound"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dpToPx(12), 0, dpToPx(4)) }
        })

        root.addView(TextView(this).apply {
            text = "Manually craft your binaural sound profile"
            textSize = 13f
            setTextColor(Color.parseColor("#AAFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(24) }
        })

        // --- Controls ---
        fun addSliderSection(label: String, min: Int, max: Int, defaultVal: Int, unit: String): SeekBar {
            val sectionLabel = TextView(this).apply {
                text = "$label: $defaultVal $unit"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dpToPx(4) }
            }
            root.addView(sectionLabel)
            val seekBar = SeekBar(this).apply {
                this.max = max - min
                progress = defaultVal - min
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5"))
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dpToPx(16) }
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        sectionLabel.text = "$label: ${p + min} $unit"
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            root.addView(seekBar)
            return seekBar
        }

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#1A3E3E"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
                .apply { bottomMargin = dpToPx(16) }
        }
        root.addView(divider)

        addSliderSection("Base Frequency (Left Ear)", 100, 500, 200, "Hz")
        addSliderSection("Beat Frequency", 1, 40, 10, "Hz")
        addSliderSection("Volume", 0, 100, 70, "%")
        addSliderSection("Vibration Intensity", 0, 100, 50, "%")
        addSliderSection("Session Duration", 5, 60, 20, "min")

        // Wave type selector
        root.addView(TextView(this).apply {
            text = "Wave Type"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(8) }
        })

        val waveTypes = listOf("Sine", "Triangle", "Square", "Sawtooth")
        val waveBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dpToPx(24) }
        }
        var selectedWave = 0
        val waveBtns = mutableListOf<TextView>()
        for ((i, wave) in waveTypes.withIndex()) {
            val btn = TextView(this).apply {
                text = wave
                textSize = 12f
                setTextColor(if (i == 0) Color.WHITE else Color.parseColor("#AAFFFF"))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(if (i == 0) Color.parseColor("#007A7A") else Color.parseColor("#1A3E3E"))
                }
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) leftMargin = dpToPx(8)
                }
                setOnClickListener {
                    selectedWave = i
                    for ((j, b) in waveBtns.withIndex()) {
                        (b.background as? GradientDrawable)?.setColor(
                            if (j == i) Color.parseColor("#007A7A") else Color.parseColor("#1A3E3E")
                        )
                        b.setTextColor(if (j == i) Color.WHITE else Color.parseColor("#AAFFFF"))
                    }
                }
            }
            waveBtns.add(btn)
            waveBtnRow.addView(btn)
        }
        root.addView(waveBtnRow)

        // Generate & Play
        val generateBtn = TextView(this).apply {
            text = "🎵  Generate & Play"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#004D5A"), Color.parseColor("#007A7A"))
            ).apply { cornerRadius = dpToPx(26).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(54))
            setOnClickListener {
                Toast.makeText(this@CustomSoundActivity, "Custom sound generated! Playing... 🎵", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(generateBtn)

        setContentView(rootFrame)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
