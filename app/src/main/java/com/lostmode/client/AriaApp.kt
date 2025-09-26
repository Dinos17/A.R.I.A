package com.lostmode.client

import android.app.Application
import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Global Application class for ARIA Client.
 * Initializes app-wide resources such as OkHttp client and context.
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

        // ARIA API Key
        val apiKey: String by lazy {
            val key = BuildConfig.ARIA_API_KEY
            if (key.isBlank()) {
                throw IllegalStateException("Missing required configuration value: ARIA_API_KEY")
            }
            key
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i("AriaApp", "Application started. Global resources initialized. API_KEY=${apiKey.take(4)}***")
    }
}