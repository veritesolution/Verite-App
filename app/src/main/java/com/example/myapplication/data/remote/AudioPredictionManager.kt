package com.example.myapplication.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class AudioPredictionManager {

    // IMPORTANT: Replace this with your actual ngrok URL from Colab output
    private val NGROK_URL = "https://sliding-supercarpal-isis.ngrok-free.dev"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun uploadAudioFile(audioFilePath: String): String = withContext(Dispatchers.IO) {
        val audioFile = File(audioFilePath)

        if (!audioFile.exists()) {
            return@withContext "Error: Audio file not found at $audioFilePath.\nPlease record an audio sample first."
        }

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", // Must match the parameter name in the FastAPI endpoint
                    audioFile.name,
                    audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$NGROK_URL/predict")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string() ?: "Empty response from server"
                } else {
                    "Error: ${response.code} - ${response.message}"
                }
            }
        } catch (e: Exception) {
            "Network Request Failed: ${e.message}"
        }
    }
}
