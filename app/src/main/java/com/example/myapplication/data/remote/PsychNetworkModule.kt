package com.example.myapplication.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.myapplication.data.model.PsychRefreshRequest
import com.example.myapplication.data.model.PsychTokenResponse
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.myapplication.BuildConfig

private const val TAG = "PsychNetwork"


class PsychTokenManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "verite_psych_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences failed, falling back to regular prefs", e)
        context.getSharedPreferences("verite_psych_prefs", Context.MODE_PRIVATE)
    }

    var accessToken: String?
        get() = prefs.getString("psych_access_token", null)
        set(value) = prefs.edit().putString("psych_access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("psych_refresh_token", null)
        set(value) = prefs.edit().putString("psych_refresh_token", value).apply()

    var username: String?
        get() = prefs.getString("psych_username", null)
        set(value) = prefs.edit().putString("psych_username", value).apply()

    val isLoggedIn: Boolean
        get() = accessToken != null

    fun saveTokens(response: PsychTokenResponse) {
        accessToken = response.accessToken
        refreshToken = response.refreshToken
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}


class PsychAuthInterceptor(
    private val tokenManager: PsychTokenManager,
    private val baseUrl: String,
) : Interceptor {

    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Skip auth for login/register/health
        val path = original.url.encodedPath
        if (path.contains("/auth/login") ||
            path.contains("/auth/register") ||
            path.contains("/health")
        ) {
            return chain.proceed(original)
        }

        val token = tokenManager.accessToken ?: return chain.proceed(original)

        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        val response = chain.proceed(authed)

        // If 401, try refreshing
        if (response.code == 401 && tokenManager.refreshToken != null) {
            response.close()
            val newTokens = refreshTokenSync()
            if (newTokens != null) {
                tokenManager.saveTokens(newTokens)
                val retried = original.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .build()
                return chain.proceed(retried)
            } else {
                tokenManager.clear()
            }
        }

        return response
    }

    private fun refreshTokenSync(): PsychTokenResponse? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val body = gson.toJson(PsychRefreshRequest(tokenManager.refreshToken ?: ""))
            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = body.toRequestBody(mediaType)

            val request = Request.Builder()
                .url("${baseUrl}api/v1/auth/refresh")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                gson.fromJson(responseBody, PsychTokenResponse::class.java)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            null
        }
    }
}

object PsychNetworkModule {

    // Uses BuildConfig.VERITE_SERVER_URL (set in local.properties)
    // Same URL source as TMR — ensures both features connect to the same server.
    // Default: "http://10.0.2.2:8000" (emulator → host)
    private val BASE_URL: String
        get() {
            val url = BuildConfig.VERITE_SERVER_URL
            return if (url.endsWith("/")) url else "$url/"
        }

    private var retrofit: Retrofit? = null
    private var apiService: PsychApiService? = null
    lateinit var tokenManager: PsychTokenManager
        private set

    fun init(context: Context) {
        tokenManager = PsychTokenManager(context.applicationContext)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = PsychAuthInterceptor(tokenManager, BASE_URL)

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)  // LLM responses can be slow
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val gson = GsonBuilder()
            .setLenient()
            .create()

        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        apiService = retrofit!!.create(PsychApiService::class.java)
    }

    fun getApi(): PsychApiService {
        return apiService ?: throw IllegalStateException(
            "PsychNetworkModule not initialized. Call PsychNetworkModule.init(context) first."
        )
    }

    fun getBaseUrl(): String = BASE_URL
}
