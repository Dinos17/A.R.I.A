package com.lostmode.client

import android.app.Application
import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Global Application class for ARIA Client.
 * Initializes app-wide resources such as OkHttp client and configuration.
 * Does not crash if ARIA_API_KEY is missing.
 */
class AriaApp : Application() {

    companion object {
        lateinit var instance: AriaApp
            private set

        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        val apiKey: String
            get() {
                val key = try {
                    BuildConfig.ARIA_API_KEY
                } catch (e: Exception) {
                    ""
                }

                if (key.isBlank() || key == "MISSING_KEY") {
                    Log.w("AriaApp", "ARIA_API_KEY missing or empty. Some features may not work.")
                }
                return key
            }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            setupEnvironment()
            Log.i("AriaApp", "Application started. Global resources initialized.")
        } catch (ex: Exception) {
            Log.e("AriaApp", "Environment setup issue: ${ex.message}", ex)
            // Do not crash; app continues
        }
    }

    private fun setupEnvironment() {
        val key = apiKey // Logs warning if missing
        Log.i("AriaApp", "Environment setup complete. API_KEY: ${if (key.isNotBlank()) "Available" else "Missing"}")
    }
}