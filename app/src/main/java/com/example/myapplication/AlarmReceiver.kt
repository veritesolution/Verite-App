package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.myapplication.data.repository.NotificationHelper

/**
 * Receives alarm broadcasts from AlarmManager and launches the fullscreen alarm UI.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_HOUR = "alarm_hour"
        const val EXTRA_ALARM_MINUTE = "alarm_minute"
        const val CHANNEL_ID = "verite_alarm_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: "unknown"
        val hour = intent.getIntExtra(EXTRA_ALARM_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_ALARM_MINUTE, 0)

        val h12 = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        val amPm = if (hour >= 12) "PM" else "AM"
        val timeStr = String.format("%d:%02d %s", h12, minute, amPm)

        // Push to notification center
        NotificationHelper.onAlarmFired(context, timeStr)

        // Launch fullscreen alarm activity
        val fullscreenIntent = Intent(context, AlarmFiredActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_HOUR, hour)
            putExtra(EXTRA_ALARM_MINUTE, minute)
        }

        // Create notification channel
        createNotificationChannel(context)

        // Build full-screen notification (required for lock screen alarm display)
        val pendingIntent = PendingIntent.getActivity(
            context, alarmId.hashCode(), fullscreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Vérité Alarm")
            .setContentText("Alarm at $timeStr")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(alarmId.hashCode(), notification)

        // Also try to start the activity directly
        try {
            context.startActivity(fullscreenIntent)
        } catch (_: Exception) {
            // Fallback: notification's fullScreenIntent will handle it
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vérité Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications from Vérité Sleep Band"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                )
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
