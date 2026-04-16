package com.example.myapplication.ui.components

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.data.model.NotificationType
import com.example.myapplication.data.repository.NotificationRepository

/**
 * Themed in-app alert system for Vérité.
 * Replaces default Android Toast with a branded slide-down banner
 * matching the dark teal aesthetic. All alerts auto-push to the notification center.
 *
 * Usage from any Activity:
 *   VeriteAlert.success(this, "Profile Updated")
 *   VeriteAlert.error(this, "Connection failed")
 *   VeriteAlert.warning(this, "Low battery on headband")
 *   VeriteAlert.info(this, "Scanning for devices...")
 */
object VeriteAlert {

    enum class AlertType {
        SUCCESS, ERROR, WARNING, INFO
    }

    private const val DURATION_SHORT = 2500L
    private const val DURATION_LONG = 4000L

    // ── Public API ──────────────────────────────────────────

    fun success(activity: Activity, message: String, long: Boolean = false, pushNotification: Boolean = true) {
        show(activity, message, AlertType.SUCCESS, if (long) DURATION_LONG else DURATION_SHORT, pushNotification)
    }

    fun error(activity: Activity, message: String, long: Boolean = true, pushNotification: Boolean = true) {
        show(activity, message, AlertType.ERROR, if (long) DURATION_LONG else DURATION_SHORT, pushNotification)
    }

    fun warning(activity: Activity, message: String, long: Boolean = false, pushNotification: Boolean = true) {
        show(activity, message, AlertType.WARNING, if (long) DURATION_LONG else DURATION_SHORT, pushNotification)
    }

    fun info(activity: Activity, message: String, long: Boolean = false, pushNotification: Boolean = false) {
        show(activity, message, AlertType.INFO, if (long) DURATION_LONG else DURATION_SHORT, pushNotification)
    }

    // ── Internal ────────────────────────────────────────────

    private fun show(activity: Activity, message: String, type: AlertType, duration: Long, pushNotification: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            val rootView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runOnUiThread

            // Remove any existing alert
            rootView.findViewWithTag<View>("verite_alert")?.let {
                rootView.removeView(it)
            }

            val config = typeConfig(type)

            // Build the alert view programmatically (no XML dependency)
            val container = LinearLayout(activity).apply {
                tag = "verite_alert"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12))

                val bg = GradientDrawable().apply {
                    setColor(config.bgColor)
                    cornerRadius = dp(activity, 16).toFloat()
                    setStroke(dp(activity, 1), config.borderColor)
                }
                background = bg
                elevation = dp(activity, 8).toFloat()
            }

            // Icon
            val icon = ImageView(activity).apply {
                setImageResource(config.iconRes)
                setColorFilter(config.accentColor)
                layoutParams = LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 22))
            }
            container.addView(icon)

            // Spacer
            val spacer = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(activity, 10), 0)
            }
            container.addView(spacer)

            // Message
            val textView = TextView(activity).apply {
                text = message
                setTextColor(config.textColor)
                textSize = 13f
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            container.addView(textView)

            // Accent bar on the left edge
            val accentBar = View(activity).apply {
                val barBg = GradientDrawable().apply {
                    setColor(config.accentColor)
                    cornerRadius = dp(activity, 2).toFloat()
                }
                background = barBg
                layoutParams = LinearLayout.LayoutParams(dp(activity, 3), dp(activity, 28)).apply {
                    marginEnd = dp(activity, 12)
                }
            }
            container.addView(accentBar, 0)

            // Wrap in a FrameLayout for positioning
            val wrapper = FrameLayout(activity).apply {
                tag = "verite_alert"
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(activity, 48)
                    leftMargin = dp(activity, 16)
                    rightMargin = dp(activity, 16)
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
                layoutParams = params
                addView(container)
            }

            rootView.addView(wrapper)

            // Slide in from top
            wrapper.translationY = -dp(activity, 120).toFloat()
            wrapper.alpha = 0f
            wrapper.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .start()

            // Auto dismiss
            Handler(Looper.getMainLooper()).postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    wrapper.animate()
                        .translationY(-dp(activity, 120).toFloat())
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction {
                            try { rootView.removeView(wrapper) } catch (_: Exception) {}
                        }
                        .start()
                }
            }, duration)

            // Push to notification center
            if (pushNotification) {
                val notifType = when (type) {
                    AlertType.SUCCESS -> NotificationType.SUCCESS
                    AlertType.ERROR -> NotificationType.ERROR
                    AlertType.WARNING -> NotificationType.WARNING
                    AlertType.INFO -> NotificationType.INFO
                }
                NotificationRepository.getInstance(activity).pushNotification(
                    title = when (type) {
                        AlertType.SUCCESS -> "Success"
                        AlertType.ERROR -> "Error"
                        AlertType.WARNING -> "Warning"
                        AlertType.INFO -> "Info"
                    },
                    message = message,
                    type = notifType
                )
            }
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }

    private data class AlertConfig(
        val bgColor: Int,
        val borderColor: Int,
        val accentColor: Int,
        val textColor: Int,
        val iconRes: Int
    )

    private fun typeConfig(type: AlertType): AlertConfig = when (type) {
        AlertType.SUCCESS -> AlertConfig(
            bgColor = 0xFF0D1E1B.toInt(),
            borderColor = 0x332DD4AA.toInt(),
            accentColor = 0xFF2DD4AA.toInt(),
            textColor = 0xFFDCE8E4.toInt(),
            iconRes = android.R.drawable.ic_menu_info_details
        )
        AlertType.ERROR -> AlertConfig(
            bgColor = 0xFF1E0D0D.toInt(),
            borderColor = 0x33F56C6C.toInt(),
            accentColor = 0xFFF56C6C.toInt(),
            textColor = 0xFFDCE8E4.toInt(),
            iconRes = android.R.drawable.ic_dialog_alert
        )
        AlertType.WARNING -> AlertConfig(
            bgColor = 0xFF1E1A0D.toInt(),
            borderColor = 0x33E6A23C.toInt(),
            accentColor = 0xFFE6A23C.toInt(),
            textColor = 0xFFDCE8E4.toInt(),
            iconRes = android.R.drawable.ic_dialog_alert
        )
        AlertType.INFO -> AlertConfig(
            bgColor = 0xFF0D1E1B.toInt(),
            borderColor = 0x331C9C91.toInt(),
            accentColor = 0xFF1C9C91.toInt(),
            textColor = 0xFFDCE8E4.toInt(),
            iconRes = android.R.drawable.ic_menu_info_details
        )
    }
}
