package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.NotificationHelper
import com.example.myapplication.data.repository.NotificationRepository
import com.example.myapplication.ui.components.VeriteAlert
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    var isActive: Boolean = true
) {
    val timeString: String
        get() = String.format(
            Locale.getDefault(), "%02d:%02d",
            if (hour > 12) hour - 12 else if (hour == 0) 12 else hour, minute
        )
    val amPm: String
        get() = if (hour >= 12) "PM" else "AM"
    val displayTime: String
        get() = "$timeString $amPm"
}

class AlarmActivity : AppCompatActivity() {

    private val alarms = mutableListOf<Alarm>()
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val taskDao by lazy { database.taskDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        findViewById<ComposeView>(R.id.composeView).setContent {
            VeriteTheme { SkyBackground { } }
        }

        loadAlarms()
        setupDashboard()
        updateTodoPreview()
        updateNextAlarmDisplay()
        setupNotificationBell()

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.profileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.fabAddAlarm).setOnClickListener {
            showTimePicker()
        }
    }

    // ── Notification Bell ───────────────────────────────────

    private fun setupNotificationBell() {
        val bellContainer = findViewById<FrameLayout>(R.id.notificationBellContainer)
        val badge = findViewById<TextView>(R.id.notificationBadge)
        val notificationOverlay = findViewById<ComposeView>(R.id.notificationPanelOverlay)

        val notifRepo = NotificationRepository.getInstance(this)

        // Observe unread count
        lifecycleScope.launch {
            notifRepo.unreadCount.collectLatest { count ->
                if (count > 0) {
                    badge.text = if (count > 99) "99+" else count.toString()
                    badge.visibility = View.VISIBLE
                } else {
                    badge.visibility = View.GONE
                }
            }
        }

        // Wire the bell to toggle the in-page notification panel
        bellContainer.setOnClickListener {
            if (notificationOverlay.visibility == View.VISIBLE) {
                notificationOverlay.visibility = View.GONE
            } else {
                notificationOverlay.visibility = View.VISIBLE
                notificationOverlay.setContent {
                    com.example.myapplication.ui.theme.VeriteTheme {
                        val notifications by notifRepo.allNotifications
                            .collectAsState(initial = emptyList())

                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .clickable { notificationOverlay.visibility = View.GONE }
                        ) {
                            com.example.myapplication.ui.notification.NotificationPanel(
                                notifications = notifications,
                                onMarkAllRead = {
                                    lifecycleScope.launch { notifRepo.markAllAsRead() }
                                },
                                onClearAll = {
                                    lifecycleScope.launch { notifRepo.clearAll() }
                                    notificationOverlay.visibility = View.GONE
                                },
                                onNotificationClick = { notif ->
                                    lifecycleScope.launch { notifRepo.markAsRead(notif.id) }
                                },
                                onDismiss = { id ->
                                    lifecycleScope.launch { notifRepo.delete(id) }
                                },
                                onClose = {
                                    notificationOverlay.visibility = View.GONE
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dashboard Cards ─────────────────────────────────────

    private fun setupDashboard() {
        findViewById<View>(R.id.boxSleepData).setOnClickListener {
            startActivity(Intent(this, SleepDataActivity::class.java))
        }
        findViewById<View>(R.id.boxTmrFeature).setOnClickListener {
            startActivity(Intent(this, TmrFeatureActivity::class.java))
        }
        findViewById<View>(R.id.boxAdaptiveMusic).setOnClickListener {
            startActivity(Intent(this, CustomSoundActivity::class.java))
        }
        findViewById<View>(R.id.boxTodoList).setOnClickListener {
            startActivity(Intent(this, MindSetActivity::class.java))
        }
        findViewById<View>(R.id.boxMorningBrief).setOnClickListener {
            startActivity(Intent(this, MorningBriefActivity::class.java))
        }
        findViewById<View>(R.id.boxBioFeedback).setOnClickListener {
            startActivity(Intent(this, BioFeedbackActivity::class.java))
        }
        findViewById<View>(R.id.boxAlarmSet).setOnClickListener {
            showTimePicker()
        }
    }

    // ── Todo Preview ────────────────────────────────────────

    private fun updateTodoPreview() {
        val container = findViewById<LinearLayout>(R.id.todoPreviewContainer)
        lifecycleScope.launch {
            taskDao.getAllFlow().collect { tasks ->
                container.removeAllViews()
                tasks.take(3).forEach { task ->
                    val itemView = LayoutInflater.from(this@AlarmActivity)
                        .inflate(R.layout.item_todo_preview, container, false)
                    itemView.findViewById<TextView>(R.id.tvTodoTask).text = task.task
                    container.addView(itemView)
                }
            }
        }
    }

    // ── Alarm Display ───────────────────────────────────────

    private fun updateNextAlarmDisplay() {
        val activeAlarm = alarms.filter { it.isActive }.minByOrNull {
            val now = Calendar.getInstance()
            val alarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, it.hour)
                set(Calendar.MINUTE, it.minute)
                set(Calendar.SECOND, 0)
            }
            if (alarmTime.before(now)) alarmTime.add(Calendar.DATE, 1)
            alarmTime.timeInMillis
        }

        activeAlarm?.let {
            val h = if (it.hour > 12) it.hour - 12 else if (it.hour == 0) 12 else it.hour
            findViewById<TextView>(R.id.tvNextAlarmHour).text =
                String.format(Locale.getDefault(), "%02d", h)
            findViewById<TextView>(R.id.tvNextAlarmMinute).text =
                String.format(Locale.getDefault(), "%02d", it.minute)
        }
    }

    // ── Time Picker + Alarm Scheduling ──────────────────────

    @android.annotation.SuppressLint("InflateParams")
    private fun showTimePicker() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_apple_time_picker, null)
        dialog.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val timePicker = view.findViewById<android.widget.TimePicker>(R.id.appleTimePicker)
        timePicker.setIs24HourView(false)

        val calendar = Calendar.getInstance()
        timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = calendar.get(Calendar.MINUTE)

        view.findViewById<TextView>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.btnSetAlarm).setOnClickListener {
            val newAlarm = Alarm(hour = timePicker.hour, minute = timePicker.minute)
            alarms.add(newAlarm)
            saveAlarms()
            scheduleAlarm(newAlarm)
            updateNextAlarmDisplay()

            // Themed success alert (before dismiss to ensure activity is still valid)
            VeriteAlert.success(this, "Alarm set for ${newAlarm.displayTime}")
            NotificationHelper.onAlarmSet(this, newAlarm.displayTime)

            dialog.dismiss()
        }

        dialog.show()
    }

    // ── AlarmManager Scheduling ─────────────────────────────

    private fun scheduleAlarm(alarm: Alarm) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_HOUR, alarm.hour)
            putExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, alarm.minute)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, alarm.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        try {
            // Android 12+ (API 31) requires canScheduleExactAlarms() check
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback: use inexact alarm (still works, just less precise)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    VeriteAlert.warning(this, "Grant exact alarm permission in Settings for precise alarms")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback if exact alarm permission denied
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            VeriteAlert.info(this, "Alarm set (approximate timing)")
        }
    }

    private fun cancelAlarm(alarm: Alarm) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, alarm.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    // ── Persistence ─────────────────────────────────────────

    private fun saveAlarms() {
        val prefs = getSharedPreferences("VeriteAlarms", Context.MODE_PRIVATE)
        val json = Gson().toJson(alarms)
        prefs.edit().putString("alarm_list", json).apply()
    }

    private fun loadAlarms() {
        val prefs = getSharedPreferences("VeriteAlarms", Context.MODE_PRIVATE)
        val json = prefs.getString("alarm_list", null)
        if (json != null) {
            val type = object : TypeToken<List<Alarm>>() {}.type
            val savedAlarms: List<Alarm> = Gson().fromJson(json, type)
            alarms.clear()
            alarms.addAll(savedAlarms)
            // Re-schedule all active alarms
            alarms.filter { it.isActive }.forEach { scheduleAlarm(it) }
        }
    }
}
