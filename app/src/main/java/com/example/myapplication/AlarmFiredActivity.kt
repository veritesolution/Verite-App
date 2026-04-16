package com.example.myapplication

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.myapplication.ui.components.VeriteAlert
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import java.util.Locale

class AlarmFiredActivity : AppCompatActivity() {

    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm_fired)

        // Compose background
        findViewById<ComposeView>(R.id.composeBackground).setContent {
            VeriteTheme { SkyBackground { } }
        }

        // Set time display
        val hour = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_HOUR, 0)
        val minute = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, 0)
        val alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID) ?: ""

        val h12 = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        val amPm = if (hour >= 12) "PM" else "AM"

        findViewById<TextView>(R.id.tvAlarmTime).text =
            String.format(Locale.getDefault(), "%d:%02d", h12, minute)
        findViewById<TextView>(R.id.tvAmPm).text = amPm

        // Pulsing glow ring animation
        val glowRing = findViewById<View>(R.id.glowRing)
        ObjectAnimator.ofFloat(glowRing, "alpha", 0.3f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(glowRing, "scaleX", 0.9f, 1.05f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(glowRing, "scaleY", 0.9f, 1.05f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Start alarm sound
        startAlarmSound()
        startVibration()

        // Dismiss button
        findViewById<View>(R.id.btnDismiss).setOnClickListener {
            stopAlarm()
            cancelNotification(alarmId)
            VeriteAlert.info(this, "Alarm dismissed", pushNotification = false)
            finish()
        }

        // Snooze button (5 minutes)
        findViewById<View>(R.id.btnSnooze).setOnClickListener {
            stopAlarm()
            cancelNotification(alarmId)
            // Schedule a new alarm 5 minutes from now
            snoozeAlarm(hour, minute)
            VeriteAlert.info(this, "Snoozed for 5 minutes", pushNotification = false)
            finish()
        }
    }

    private fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, alarmUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
        } catch (_: Exception) { }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 300, 500, 300, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) { }
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    private fun cancelNotification(alarmId: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(alarmId.hashCode())
    }

    private fun snoozeAlarm(originalHour: Int, originalMinute: Int) {
        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MINUTE, 5)
        }
        val snoozeAlarm = Alarm(
            hour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            minute = calendar.get(java.util.Calendar.MINUTE)
        )
        // Schedule with AlarmManager
        val intent = android.content.Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, snoozeAlarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_HOUR, snoozeAlarm.hour)
            putExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, snoozeAlarm.minute)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, snoozeAlarm.id.hashCode(), intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Android 12+ requires canScheduleExactAlarms() check
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm if permission not granted
                    am.setAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                am.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback to basic alarm on SecurityException
            android.util.Log.e("AlarmFiredActivity", "Exact alarm permission denied, using fallback", e)
            am.set(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
