package com.example.myapplication.data.network

import com.example.myapplication.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class HuggingFaceHelper {
    private val client = OkHttpClient()
    private val apiKey = Secrets.HF_API_KEY
    private val modelUrl = "https://api-inference.huggingface.co/models/HuggingFaceH4/zephyr-7b-beta"

    suspend fun generateMorningBrief(name: String, city: String, temp: Double): String? = withContext(Dispatchers.IO) {
        try {
            // Zephyr prompt instructions format
            val prompt = "<|system|>\nYou are a positive, uplifting AI assistant. <|end|>\n<|user|>\nWrite a very short, inspiring 2-sentence morning greeting for $name. They just woke up in $city where it is currently $temp°C. Output ONLY the greeting itself, no additional text or explanations.<|end|>\n<|assistant|>\n"
            
            val jsonBody = JSONObject().apply {
                put("inputs", prompt)
                put("parameters", JSONObject().apply {
                    put("max_new_tokens", 60)
                    put("return_full_text", false)
                    put("temperature", 0.7)
                })
            }
            
            val request = Request.Builder()
                .url(modelUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // API might return 503 "Model is loading"
                    return@withContext "Good Morning $name! The AI is just waking up, but I hope you have an incredible day today in $city!"
                }
                
                val responseBody = response.body?.string() ?: return@withContext null
                
                // Response is typically a JSON array: [{"generated_text": "..."}]
                val array = JSONArray(responseBody)
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    var text = obj.getString("generated_text").trim()
                    
                    // Cleanup any stray tokens if the model ignored return_full_text
                    if (text.contains("<|assistant|>")) {
                        text = text.substringAfter("<|assistant|>").trim()
                    }
                    text.replace("\"", "").trim()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
