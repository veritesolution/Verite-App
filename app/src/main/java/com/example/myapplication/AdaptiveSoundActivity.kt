package com.example.myapplication

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AdaptiveSoundActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---------- Root ScrollView ----------
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundResource(R.drawable.group_1000006461)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, dpToPx(32))
        }
        scrollView.addView(root)

        // ---------- Header ----------
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dpToPx(32), 0, 0) }
            setPadding(dpToPx(20), 0, dpToPx(20), 0)
        }

        val backBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            setOnClickListener { finish() }
        }

        val titleSp = SpannableString("Vérité")
        titleSp.setSpan(ForegroundColorSpan(Color.WHITE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleSp.setSpan(ForegroundColorSpan(Color.parseColor("#00BFA5")), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        titleSp.setSpan(ForegroundColorSpan(Color.WHITE), 2, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val titleTv = TextView(this).apply {
            text = titleSp
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        }

        val profileIv = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_myplaces)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
        }

        headerRow.addView(backBtn)
        headerRow.addView(titleTv)
        headerRow.addView(profileIv)
        root.addView(headerRow)

        // ---------- Hero: Sleep Band Image ----------
        val headbandIv = ImageView(this).apply {
            val resId = resources.getIdentifier("sleep_band", "drawable", packageName)
            if (resId != 0) setImageResource(resId) else setImageResource(android.R.drawable.ic_lock_idle_low_battery)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(dpToPx(260), dpToPx(160)).apply {
                gravity = Gravity.CENTER
                topMargin = dpToPx(16)
                bottomMargin = dpToPx(4)
            }
        }
        val headWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(headbandIv)
        }
        root.addView(headWrap)

        // ---------- Screen Title ----------
        val screenTitle = TextView(this).apply {
            text = "Adaptive Sound"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(4)
            }
        }
        root.addView(screenTitle)

        val subTitle = TextView(this).apply {
            text = "Choose your sound mode"
            textSize = 13f
            setTextColor(Color.parseColor("#AAFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(20) }
        }
        root.addView(subTitle)

        // ---------- 4 Mode Cards ----------
        data class ModeInfo(
            val name: String,
            val emoji: String,
            val desc: String,
            val topColor: String,
            val botColor: String,
            val activityClass: Class<*>
        )

        val modes = listOf(
            ModeInfo(
                "Focus", "🎯",
                "Binaural beats to boost concentration.\nSession report + progress tracking.",
                "#004D5A", "#007A7A",
                FocusSoundActivity::class.java
            ),
            ModeInfo(
                "Relax", "🌊",
                "Calming binaural sounds + vibration.\nStress report: before & after results.",
                "#0A2A1A", "#1A6B40",
                RelaxSoundActivity::class.java
            ),
            ModeInfo(
                "Sleep", "🌙",
                "Sleepy binaural sounds + gentle vibrations\nto help you drift off peacefully.",
                "#0D0A2E", "#2A1A6E",
                SleepSoundActivity::class.java
            ),
            ModeInfo(
                "Meditate", "🧘",
                "Binaural sounds + biofeedback to analyze\nbrain activity & track meditation progress.",
                "#1A0A2A", "#4A1A6A",
                MeditateActivity::class.java
            )
        )

        for (mode in modes) {
            val card = buildModeCard(mode.name, mode.emoji, mode.desc, mode.topColor, mode.botColor) {
                startActivity(Intent(this, mode.activityClass))
            }
            root.addView(card)
        }

        // ---------- Custom Sound Button ----------
        val customBtn = TextView(this).apply {
            text = "🎵  Create Custom Sound"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(28).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00BFA5"))
                setColor(Color.parseColor("#1A2E2E"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)
            ).apply {
                setMargins(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(4))
            }
            setOnClickListener {
                startActivity(Intent(this@AdaptiveSoundActivity, CustomSoundActivity::class.java))
            }
        }
        root.addView(customBtn)

        setContentView(scrollView)

        // Entrance animations
        animateEntrance(root)
    }

    private fun buildModeCard(
        name: String,
        emoji: String,
        desc: String,
        topColor: String,
        botColor: String,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor(topColor), Color.parseColor(botColor))
            ).apply { cornerRadius = dpToPx(18).toFloat() }
            setPadding(dpToPx(16), dpToPx(18), dpToPx(16), dpToPx(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpToPx(20), dpToPx(10), dpToPx(20), 0) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        // Emoji circle
        val emojiTv = TextView(this).apply {
            text = emoji
            textSize = 30f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22FFFFFF"))
                setSize(dpToPx(56), dpToPx(56))
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(56), dpToPx(56))
        }

        // Text block
        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dpToPx(16)
            }
        }

        val nameTv = TextView(this).apply {
            text = name
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }

        val descTv = TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
        }

        textBlock.addView(nameTv)
        textBlock.addView(descTv)

        // Arrow
        val arrow = TextView(this).apply {
            text = "›"
            textSize = 28f
            setTextColor(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        card.addView(emojiTv)
        card.addView(textBlock)
        card.addView(arrow)

        // Hover effect
        card.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    v.performClick()
                }
            }
            true
        }

        return card
    }

    private fun animateEntrance(root: ViewGroup) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            child.alpha = 0f
            child.translationY = 60f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((i * 80).toLong())
                .setDuration(400)
                .start()
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
