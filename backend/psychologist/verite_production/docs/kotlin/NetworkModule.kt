// ═══════════════════════════════════════════════════════════════
// File: data/remote/NetworkModule.kt
// Retrofit client setup with JWT auth interceptor
// Add to: app/src/main/java/com/yourapp/data/remote/
//
// Gradle dependencies needed:
//   implementation("com.squareup.retrofit2:retrofit:2.9.0")
//   implementation("com.squareup.retrofit2:converter-gson:2.9.0")
//   implementation("com.squareup.okhttp3:okhttp:4.12.0")
//   implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
//   implementation("com.google.code.gson:gson:2.10.1")
//   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
//   implementation("androidx.security:security-crypto:1.1.0-alpha06")
// ═══════════════════════════════════════════════════════════════

package com.yourapp.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.yourapp.data.model.RefreshRequest
import com.yourapp.data.model.TokenResponse
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════
// Secure Token Storage
// ═══════════════════════════════════════════════════════════════

class TokenManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "verite_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var username: String?
        get() = prefs.getString("username", null)
        set(value) = prefs.edit().putString("username", value).apply()

    val isLoggedIn: Boolean
        get() = accessToken != null

    fun saveTokens(response: TokenResponse) {
        accessToken = response.accessToken
        refreshToken = response.refreshToken
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

// ═══════════════════════════════════════════════════════════════
// Auth Interceptor — auto-attaches JWT, auto-refreshes on 401
// ═══════════════════════════════════════════════════════════════

class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val baseUrl: String,
) : Interceptor {

    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Skip auth for login/register/health endpoints
        val path = original.url.encodedPath
        if (path.contains("/auth/login") ||
            path.contains("/auth/register") ||
            path.contains("/health")
        ) {
            return chain.proceed(original)
        }

        // Attach access token
        val token = tokenManager.accessToken
        if (token == null) {
            return chain.proceed(original)
        }

        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        val response = chain.proceed(authed)

        // If 401, try refreshing the token
        if (response.code == 401 && tokenManager.refreshToken != null) {
            response.close()

            val newTokens = refreshTokenSync()
            if (newTokens != null) {
                tokenManager.saveTokens(newTokens)

                // Retry with new token
                val retried = original.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .build()
                return chain.proceed(retried)
            } else {
                // Refresh failed — user needs to re-login
                tokenManager.clear()
            }
        }

        return response
    }

    private fun refreshTokenSync(): TokenResponse? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val body = gson.toJson(RefreshRequest(tokenManager.refreshToken ?: ""))
            val mediaType = okhttp3.MediaType.Companion.parse("application/json")
            val requestBody = okhttp3.RequestBody.Companion.create(mediaType, body)

            val request = Request.Builder()
                .url("${baseUrl}api/v1/auth/refresh")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                gson.fromJson(responseBody, TokenResponse::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Network Module — Creates Retrofit instance
// ═══════════════════════════════════════════════════════════════

object NetworkModule {

    // CHANGE THIS to your server URL
    private const val BASE_URL = "http://10.0.2.2:8000/"  // Android emulator → localhost
    // For physical device on same WiFi: "http://192.168.x.x:8000/"
    // For production: "https://your-domain.com/"

    private var retrofit: Retrofit? = null
    private var apiService: VeriteApiService? = null
    lateinit var tokenManager: TokenManager
        private set

    fun init(context: Context) {
        tokenManager = TokenManager(context.applicationContext)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY  // Change to NONE in production
        }

        val authInterceptor = AuthInterceptor(tokenManager, BASE_URL)

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

        apiService = retrofit!!.create(VeriteApiService::class.java)
    }

    fun getApi(): VeriteApiService {
        return apiService ?: throw IllegalStateException(
            "NetworkModule not initialized. Call NetworkModule.init(context) in Application.onCreate()"
        )
    }

    fun getBaseUrl(): String = BASE_URL
}
