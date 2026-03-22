package com.mindsetpro.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mindsetpro.data.model.Task
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Task & Habit Scheduler with local notifications.
 *
 * Features:
 *   • Upcoming task reminders (24h, 1h, 15min before due)
 *   • Daily habit reminder at configurable time
 *   • Bedtime routine trigger
 *   • Streak-at-risk warnings
 */
object TaskScheduler {

    const val CHANNEL_TASKS = "mindsetpro_tasks"
    const val CHANNEL_HABITS = "mindsetpro_habits"
    const val CHANNEL_BEDTIME = "mindsetpro_bedtime"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val channels = listOf(
                NotificationChannel(CHANNEL_TASKS, "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders for upcoming and overdue tasks"
                },
                NotificationChannel(CHANNEL_HABITS, "Habit Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Daily habit completion reminders"
                },
                NotificationChannel(CHANNEL_BEDTIME, "Bedtime Routine",
                    NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Nightly bedtime routine trigger"
                }
            )
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    /**
     * Check for tasks due within the next 24 hours.
     */
    fun getUpcomingTasks(tasks: List<Task>): List<Pair<Task, Long>> {
        val now = LocalDateTime.now()
        return tasks
            .filter { !it.done && it.dueTime != null }
            .mapNotNull { task ->
                try {
                    val due = LocalDateTime.parse(task.dueTime)
                    val minutesUntil = ChronoUnit.MINUTES.between(now, due)
                    if (minutesUntil in 0..1440) task to minutesUntil else null
                } catch (_: Exception) { null }
            }
            .sortedBy { it.second }
    }

    /**
     * Format time-until-due as human-readable string.
     */
    fun formatTimeUntil(minutes: Long): String = when {
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes / 1440}d"
    }

    /**
     * Schedule a local notification for a specific time.
     */
    fun scheduleNotification(
        context: Context,
        title: String,
        message: String,
        triggerAtMillis: Long,
        requestCode: Int,
        channel: String = CHANNEL_TASKS
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("channel", channel)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
        )
    }

    /**
     * Schedule daily habit reminder.
     */
    fun scheduleDailyHabitReminder(context: Context, hour: Int = 9, minute: Int = 0) {
        val now = LocalDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0)
        if (target.isBefore(now)) target = target.plusDays(1)
        val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduleNotification(
            context = context,
            title = "🎯 MindSet Pro — Daily Habits",
            message = "Time to check in on today's habits! Stay consistent.",
            triggerAtMillis = millis,
            requestCode = 9001,
            channel = CHANNEL_HABITS
        )
    }

    /**
     * Schedule bedtime routine notification.
     */
    fun scheduleBedtimeReminder(context: Context, hour: Int = 22, minute: Int = 0) {
        val now = LocalDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0)
        if (target.isBefore(now)) target = target.plusDays(1)
        val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduleNotification(
            context = context,
            title = "🌙 Bedtime Routine",
            message = "Time to wind down. Start your bedtime routine!",
            triggerAtMillis = millis,
            requestCode = 9002,
            channel = CHANNEL_BEDTIME
        )
    }
}

/**
 * BroadcastReceiver to display scheduled notifications.
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "MindSet Pro"
        val message = intent.getStringExtra("message") ?: ""
        val channel = intent.getStringExtra("channel") ?: TaskScheduler.CHANNEL_TASKS

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
