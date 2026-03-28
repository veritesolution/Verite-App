package com.verite.tmr.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit + OkHttp client for the Vérité TMR REST API.
 * Call configure() exactly once before using [api] — in Application.onCreate().
 */
object VeriteClient {

    private var _api: VeriteApi? = null
    private var _retrofit: Retrofit? = null
    private var _baseUrl: String = ""
    private var _apiKey: String = ""

    val api: VeriteApi
        get() = _api ?: error("VeriteClient not configured. Call configure() in Application.onCreate().")

    val baseUrl: String get() = _baseUrl
    val apiKey: String get() = _apiKey

    val wsBaseUrl: String
        get() = _baseUrl.replace("https://", "wss://").replace("http://", "ws://")

    fun configure(baseUrl: String, apiKey: String, debug: Boolean = false) {
        _baseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        _apiKey = apiKey

        val logging = HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.BASIC
        }

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-API-Key", _apiKey)
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .retryOnConnectionFailure(true)
            .build()

        _retrofit = Retrofit.Builder()
            .baseUrl(_baseUrl)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        _api = _retrofit!!.create(VeriteApi::class.java)
    }

    /**
     * Create any Retrofit service interface from the configured client.
     * Used by Hilt module to create StudyApi from the same Retrofit instance.
     */
    fun <T> createService(serviceClass: Class<T>): T {
        val retrofit = _retrofit ?: error("VeriteClient not configured.")
        return retrofit.create(serviceClass)
    }

    internal fun buildWsHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
}
