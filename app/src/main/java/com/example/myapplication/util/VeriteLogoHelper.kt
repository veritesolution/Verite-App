package com.example.myapplication.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import com.example.myapplication.R

/**
 * Applies the branded Vérité logo styling to a TextView.
 *
 * Logo breakdown:
 *   V      → White, Italic Serif (Cormorant Garamond)
 *   érit   → White, Light Sans-serif (Outfit)
 *   é      → Teal (#00BFA5), Italic Serif (Cormorant Garamond)
 *
 * Matches the Compose implementation in TopBar.kt / VeriteTopBar.kt.
 */
object VeriteLogoHelper {

    private const val TEAL = "#00BFA5"

    /**
     * Style a TextView with the branded Vérité mixed-font logo.
     * Downloads Google Fonts asynchronously so the typeface swaps in seamlessly.
     */
    fun applyLogoStyle(textView: TextView) {
        val text = "Vérité"
        val spannable = SpannableString(text)

        // ── Colors ───────────────────────────────────────────────────
        // V (index 0)      → White
        spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // érit (index 1-4) → White
        spannable.setSpan(ForegroundColorSpan(Color.WHITE), 1, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // é (index 5)      → Teal
        spannable.setSpan(ForegroundColorSpan(Color.parseColor(TEAL)), 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // ── Font styles (fallback italic for V and final é) ─────────
        spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.NORMAL), 1, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        textView.text = spannable

        // ── Try loading Google Fonts for richer styling ──────────────
        loadCormorantForSpans(textView, spannable)
    }

    /**
     * Asynchronously load Cormorant Garamond Italic and apply it ONLY
     * to the "V" and final "é" characters via CustomTypefaceSpan.
     */
    private fun loadCormorantForSpans(textView: TextView, spannable: SpannableString) {
        val context = textView.context
        try {
            val request = FontRequest(
                "com.google.android.gms.fonts",
                "com.google.android.gms",
                "name=Cormorant Garamond&weight=500&italic=1",
                R.array.com_google_android_gms_fonts_certs
            )

            FontsContractCompat.requestFont(
                context,
                request,
                object : FontsContractCompat.FontRequestCallback() {
                    override fun onTypefaceRetrieved(typeface: Typeface) {
                        // Apply serif italic to V and final é
                        spannable.setSpan(
                            CustomTypefaceSpan(typeface),
                            0, 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            CustomTypefaceSpan(typeface),
                            5, 6,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        textView.text = spannable
                    }

                    override fun onTypefaceRequestFailed(reason: Int) {
                        // Fallback already applied via StyleSpan — looks fine
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
        } catch (_: Exception) {
            // Font loading failed — fallback italic already applied
        }
    }

    /**
     * A TypefaceSpan that applies a custom Typeface to specific characters.
     */
    private class CustomTypefaceSpan(
        private val customTypeface: Typeface
    ) : android.text.style.MetricAffectingSpan() {

        override fun updateDrawState(ds: android.text.TextPaint) {
            ds.typeface = customTypeface
        }

        override fun updateMeasureState(paint: android.text.TextPaint) {
            paint.typeface = customTypeface
        }
    }
}
