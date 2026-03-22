package com.example.myapplication.data.logic

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CalibrationManager(private val apiBaseUrl: String, private val apiKey: String) {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun addCalibrationWindow(f3: FloatArray, f4: FloatArray): Boolean {
        val body = gson.toJson(mapOf("f3" to f3.toList(), "f4" to f4.toList()))
        val request = Request.Builder()
            .url("$apiBaseUrl/api/v1/calibration/add")
            .addHeader("X-API-Key", apiKey)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            try {
                val resp = client.newCall(request).execute()
                val json = gson.fromJson(resp.body?.string(), Map::class.java)
                json["status"] == "ok"
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun finalizeCalibration(): Boolean {
        val request = Request.Builder()
            .url("$apiBaseUrl/api/v1/calibration/finalize")
            .addHeader("X-API-Key", apiKey)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            try {
                val resp = client.newCall(request).execute()
                val json = gson.fromJson(resp.body?.string(), Map::class.java)
                json["is_calibrated"] == true
            } catch (e: Exception) {
                false
            }
        }
    }
}
