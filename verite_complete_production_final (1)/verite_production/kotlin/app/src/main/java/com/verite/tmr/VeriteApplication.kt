package com.verite.tmr

import android.app.Application
import com.verite.tmr.data.network.VeriteClient
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class.
 *
 * Must be declared in AndroidManifest.xml:
 *   <application android:name=".VeriteApplication" ...>
 *
 * Configures VeriteClient once, before any Activity or Service starts.
 * BuildConfig values come from local.properties via build.gradle.kts.
 */
@HiltAndroidApp
class VeriteApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configure the REST + WebSocket client.
        // BuildConfig.VERITE_SERVER_URL and VERITE_API_KEY are injected from
        // local.properties at build time — never hardcode values here.
        VeriteClient.configure(
            baseUrl = BuildConfig.VERITE_SERVER_URL,
            apiKey  = BuildConfig.VERITE_API_KEY,
            debug   = BuildConfig.DEBUG
        )
    }
}
