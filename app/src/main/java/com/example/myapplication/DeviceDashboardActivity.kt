package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.Device
import com.example.myapplication.data.model.DeviceType
import com.example.myapplication.data.repository.DeviceRepository
import com.example.myapplication.util.ProfileIconHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class DeviceDashboardActivity : AppCompatActivity() {

    private lateinit var deviceRepository: DeviceRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeviceAdapter
    private val devices = mutableListOf<Device>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_dashboard)

        val database = AppDatabase.getDatabase(this)
        deviceRepository = DeviceRepository(database.deviceDao())

        setupHeader(database)
        setupRecyclerView()
        setupBottomNav()

        val fab = findViewById<FloatingActionButton>(R.id.fabAddDevice)
        fab.setOnClickListener {
            // Placeholder for "Add Device" feature
            Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show()
        }
        val pulseAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.anim_pulse)
        fab.startAnimation(pulseAnimation)

        lifecycleScope.launch {
            deviceRepository.allDevices.collect { deviceList ->
                if (deviceList.isEmpty()) {
                    deviceRepository.initializeSampleDevices()
                } else {
                    devices.clear()
                    devices.addAll(deviceList)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun setupHeader(database: AppDatabase) {
        val profileIcon = findViewById<ImageView>(R.id.profileIcon)
        profileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        ProfileIconHelper.syncProfileIcon(this, profileIcon)

        val tvAppTitle = findViewById<TextView>(R.id.tvAppTitle)
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(tvAppTitle)
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.deviceRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(devices) { device ->
            val intent = when (device.type) {
                DeviceType.SLEEP_BAND -> Intent(this@DeviceDashboardActivity, HeadbandHomeActivity::class.java)
                DeviceType.SMART_BACKREST -> Intent(this@DeviceDashboardActivity, BackrestHomeActivity::class.java)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    private fun setupBottomNav() {
        findViewById<View>(R.id.navHome).setOnClickListener {
            // Assuming this is Home
            finish()
        }
        findViewById<View>(R.id.navOryn).setOnClickListener {
            startActivity(Intent(this, OrynActivity::class.java))
        }
        findViewById<View>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private inner class DeviceAdapter(
        private val list: List<Device>,
        private val onClick: (Device) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivDeviceImage: ImageView = view.findViewById(R.id.ivDeviceImage)
            val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
            val tvDeviceStatus: TextView = view.findViewById(R.id.tvDeviceStatus)

            init {
                view.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onClick(list[adapterPosition])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = list[position]
            holder.tvDeviceName.text = device.name
            
            holder.tvDeviceStatus.text = if (device.isConnected) "Connected" else "Not Connected"
            holder.tvDeviceStatus.setTextColor(
                if (device.isConnected) android.graphics.Color.GREEN 
                else android.graphics.Color.parseColor("#00BFA5")
            )

            val imageResId = resources.getIdentifier(device.imageResource, "drawable", packageName)
            if (imageResId != 0) {
                holder.ivDeviceImage.setImageResource(imageResId)
            } else {
                holder.ivDeviceImage.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        }

        override fun getItemCount() = list.size
    }
}
