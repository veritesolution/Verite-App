package com.example.myapplication.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class WeatherInfo(
    val temperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val uvIndex: Double
)

class WeatherHelper {
    private val client = OkHttpClient()

    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m,uv_index"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val responseBody = response.body?.string() ?: return@withContext null
                val json = JSONObject(responseBody)
                val current = json.getJSONObject("current")
                
                WeatherInfo(
                    temperature = current.getDouble("temperature_2m"),
                    humidity = current.getInt("relative_humidity_2m"),
                    windSpeed = current.getDouble("wind_speed_10m"),
                    uvIndex = if (current.has("uv_index") && !current.isNull("uv_index")) current.getDouble("uv_index") else 0.0
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
