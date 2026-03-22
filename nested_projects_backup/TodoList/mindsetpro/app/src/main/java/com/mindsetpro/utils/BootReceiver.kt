package com.mindsetpro.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-schedules alarms after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TaskScheduler.createNotificationChannels(context)
            TaskScheduler.scheduleDailyHabitReminder(context)
            TaskScheduler.scheduleBedtimeReminder(context)
        }
    }
}
