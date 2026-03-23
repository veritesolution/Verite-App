package com.example.myapplication

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.Task
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.TmrFeatureActivity
import com.example.myapplication.TodoActivity
import com.example.myapplication.DreamJournalActivity
import com.example.myapplication.BioFeedbackActivity
import com.example.myapplication.SleepDataActivity
import com.example.myapplication.CustomSoundActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.*

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    var isActive: Boolean = true
) {
    val timeString: String
        get() = String.format(Locale.getDefault(), "%02d:%02d", if (hour > 12) hour - 12 else if (hour == 0) 12 else hour, minute)
    val amPm: String
        get() = if (hour >= 12) "PM" else "AM"
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

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.profileIcon).setOnClickListener {
            startActivity(android.content.Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.fabAddAlarm).setOnClickListener {
            showTimePicker()
        }
    }

    private fun setupDashboard() {
        findViewById<View>(R.id.boxSleepData).setOnClickListener {
            startActivity(android.content.Intent(this, SleepDataActivity::class.java))
        }
        findViewById<View>(R.id.boxTmrFeature).setOnClickListener {
            startActivity(android.content.Intent(this, TmrFeatureActivity::class.java))
        }
        findViewById<View>(R.id.boxAdaptiveMusic).setOnClickListener {
            startActivity(android.content.Intent(this, CustomSoundActivity::class.java))
        }
        findViewById<View>(R.id.boxTodoList).setOnClickListener {
            startActivity(android.content.Intent(this, MindSetActivity::class.java))
        }
        findViewById<View>(R.id.boxMorningBrief).setOnClickListener {
            startActivity(android.content.Intent(this, MorningBriefActivity::class.java))
        }
        findViewById<View>(R.id.boxBioFeedback).setOnClickListener {
            startActivity(android.content.Intent(this, BioFeedbackActivity::class.java))
        }
        findViewById<View>(R.id.boxAlarmSet).setOnClickListener {
            // Maybe show a list of all alarms in a dialog or just use the FAB
            showTimePicker()
        }
    }

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

    private fun updateNextAlarmDisplay() {
        val activeAlarm = alarms.filter { it.isActive }.minByOrNull { 
            // Simple logic for next alarm in 24h
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
            findViewById<TextView>(R.id.tvNextAlarmHour).text = String.format(Locale.getDefault(), "%02d", h)
            findViewById<TextView>(R.id.tvNextAlarmMinute).text = String.format(Locale.getDefault(), "%02d", it.minute)
        }
    }

    @android.annotation.SuppressLint("InflateParams")
    private fun showTimePicker() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_apple_time_picker, null)
        dialog.setContentView(view)
        
        // Ensure background of bottom sheet is transparent so our custom rounded glass background shows well
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
            updateNextAlarmDisplay()
            dialog.dismiss()
        }

        dialog.show()
    }

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
        }
    }
}
