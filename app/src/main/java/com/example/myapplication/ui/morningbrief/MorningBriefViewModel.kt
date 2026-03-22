package com.example.myapplication.ui.morningbrief

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.Task
import com.example.myapplication.data.network.HuggingFaceHelper
import com.example.myapplication.data.network.WeatherHelper
import com.example.myapplication.data.network.WeatherInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

class MorningBriefViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {
    private val taskDao = AppDatabase.getDatabase(application).taskDao()
    private val userDao = AppDatabase.getDatabase(application).userDao()
    private val weatherHelper = WeatherHelper()
    private val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val geocoder = Geocoder(application, Locale.getDefault())
    private val huggingFaceHelper = HuggingFaceHelper()

    private val _weatherState = MutableStateFlow<WeatherInfo?>(null)
    val weatherState: StateFlow<WeatherInfo?> = _weatherState.asStateFlow()

    private val _cityName = MutableStateFlow<String>("Detecting Location...")
    val cityName: StateFlow<String> = _cityName.asStateFlow()

    private val _aiGreeting = MutableStateFlow<String?>(null)
    val aiGreeting: StateFlow<String?> = _aiGreeting.asStateFlow()

    private val _pendingTasks = MutableStateFlow<List<Task>>(emptyList())
    val pendingTasks: StateFlow<List<Task>> = _pendingTasks.asStateFlow()

    private val _isSpeaking = MutableStateFlow<Boolean>(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null

    init {
        loadTasks()
        tts = TextToSpeech(application, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            taskDao.getAllFlow().collect { tasks ->
                _pendingTasks.value = tasks.filter { !it.isCompleted }.take(6)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchLocationAndWeather() {
        try {
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            
            for (provider in providers) {
                val l = locationManager.getLastKnownLocation(provider)
                if (l != null && (bestLocation == null || l.accuracy < bestLocation.accuracy)) {
                    bestLocation = l
                }
            }

            if (bestLocation != null) {
                processLocation(bestLocation)
            } else {
                // Request a single update if last known is null
                if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, object : LocationListener {
                        override fun onLocationChanged(location: Location) { processLocation(location) }
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }, null)
                } else {
                    _cityName.value = "Location Unavailable"
                }
            }
        } catch (e: Exception) {
            _cityName.value = "Location Error"
        }
    }

    private fun processLocation(location: Location) {
        viewModelScope.launch {
            try {
                // Not all devices support Geocoder
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    _cityName.value = addresses[0].locality ?: addresses[0].subAdminArea ?: addresses[0].adminArea ?: "Unknown Location"
                } else {
                    _cityName.value = "Unknown Location"
                }
            } catch (e: Exception) {
                _cityName.value = "Location Found"
            }

            val weather = weatherHelper.getCurrentWeather(location.latitude, location.longitude)
            if (weather != null) {
                _weatherState.value = weather
            }
            
            // Fetch real user name
            val localUser = userDao.getUser(1).firstOrNull()
            val realName = localUser?.name?.takeIf { it.isNotBlank() } ?: "User"

            // Generate AI Greeting
            _aiGreeting.value = "Consulting AI for your morning brief..."
            val resolvedCity = if (_cityName.value == "Unknown Location") "your city" else _cityName.value
            val temp = weather?.temperature ?: 25.0
            
            val greeting = huggingFaceHelper.generateMorningBrief(
                name = realName,
                city = resolvedCity,
                temp = temp
            )
            _aiGreeting.value = greeting ?: "Good Morning $realName! Have an amazing and productive day today."
        }
    }

    fun toggleVoiceAssistant() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
            _isSpeaking.value = false
        } else {
            val greetingText = _aiGreeting.value ?: "Good Morning!"
            val taskList = _pendingTasks.value
            
            val speechText = buildString {
                append(greetingText)
                append(" ")
                if (taskList.isNotEmpty()) {
                    append("You have ${taskList.size} tasks on your health brief today. ")
                    val topTasks = taskList.take(3)
                    topTasks.forEachIndexed { index, task ->
                        append("Task ${index + 1} is ${task.task}. ")
                    }
                } else {
                    append("You have no pending tasks today. Enjoy your day!")
                }
            }
            
            tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "MorningBrief")
            _isSpeaking.value = true
        }
    }
}
