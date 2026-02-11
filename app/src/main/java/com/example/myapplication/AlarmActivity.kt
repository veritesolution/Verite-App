package com.example.myapplication

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    var isActive: Boolean = true
) {
    val timeString: String
        get() {
            val h = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
            return String.format("%02d:%02d", h, minute)
        }
    val amPm: String
        get() = if (hour >= 12) "PM" else "AM"
}

class AlarmActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AlarmAdapter
    private val alarms = mutableListOf<Alarm>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        loadAlarms()

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.alarmRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AlarmAdapter(alarms, { saveAlarms() }, { alarm -> deleteAlarm(alarm) })
        recyclerView.adapter = adapter

        findViewById<View>(R.id.fabAddAlarm).setOnClickListener {
            showTimePicker()
        }
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val newAlarm = Alarm(hour = hour, minute = minute)
            alarms.add(newAlarm)
            saveAlarms()
            adapter.notifyItemInserted(alarms.size - 1)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun deleteAlarm(alarm: Alarm) {
        val index = alarms.indexOf(alarm)
        if (index != -1) {
            alarms.removeAt(index)
            saveAlarms()
            adapter.notifyItemRemoved(index)
        }
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

    class AlarmAdapter(
        private val list: List<Alarm>,
        private val onUpdate: () -> Unit,
        private val onDelete: (Alarm) -> Unit
    ) : RecyclerView.Adapter<AlarmAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTime: TextView = v.findViewById(R.id.tvAlarmTime)
            val tvAmPm: TextView = v.findViewById(R.id.tvAlarmAmPm)
            val switch: SwitchMaterial = v.findViewById(R.id.switchAlarm)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val alarm = list[position]
            holder.tvTime.text = alarm.timeString
            holder.tvAmPm.text = alarm.amPm
            holder.switch.isChecked = alarm.isActive
            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                alarm.isActive = isChecked
                onUpdate()
            }
            holder.btnDelete.setOnClickListener { onDelete(alarm) }
        }

        override fun getItemCount() = list.size
    }
}
