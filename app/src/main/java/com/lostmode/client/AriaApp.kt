package com.lostmode.client

import android.app.Application
import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Global Application class for ARIA Client.
 * Initializes app-wide resources such as OkHttp client, context, and required configuration.
 * Ensures ARIA_API_KEY is present before continuing.
 */
class AriaApp : Application() {

    companion object {
        lateinit var instance: AriaApp
            private set

        // Shared OkHttp client for the whole app
        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        // Access the ARIA API key safely
        val apiKey: String
            get() {
                val key = BuildConfig.ARIA_API_KEY
                if (key.isBlank() || key == "MISSING_KEY") {
                    Log.e("AriaApp", "Missing required configuration value: ARIA_API_KEY")
                    throw IllegalStateException("ARIA_API_KEY is missing! Check your local.properties and build.gradle setup.")
                }
                return key
            }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize app environment
        try {
            setupEnvironment()
            Log.i("AriaApp", "Application started. Global resources initialized.")
        } catch (ex: IllegalStateException) {
            Log.e("AriaApp", "Initialization failed: ${ex.message}", ex)
            throw ex // fatal if configuration is missing
        }
    }

    /**
     * Ensures the environment is correctly configured before the app runs.
     */
    private fun setupEnvironment() {
        // Force access to apiKey property; will throw if missing
        val key = apiKey

        // Here you can add additional initialization logic if needed
        // e.g., preloading resources, initializing SDKs, etc.
        Log.i("AriaApp", "Environment setup complete. ARIA_API_KEY available.")
    }
}