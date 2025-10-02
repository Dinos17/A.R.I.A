package com.lostmode.client

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Global Application class for ARIA Client.
 * Initializes app-wide resources such as OkHttp client and configuration.
 * Handles automatic device registration and unregistration.
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

            // Automatically register device if token exists
            registerDeviceIfNeeded()
        } catch (ex: Exception) {
            Log.e("AriaApp", "Environment setup issue: ${ex.message}", ex)
        }
    }

    private fun setupEnvironment() {
        val key = apiKey
        Log.i("AriaApp", "Environment setup complete. API_KEY: ${if (key.isNotBlank()) "Available" else "Missing"}")
    }

    /**
     * Registers the device with the server if it has not been registered yet.
     */
    private fun registerDeviceIfNeeded() {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val deviceId = prefs.getInt("selected_device_id", -1)

        if (deviceId == -1) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Gather basic device info
                    val deviceInfo = JSONObject().apply {
                        put("name", Build.MODEL)
                        put("manufacturer", Build.MANUFACTURER)
                        put("android_version", Build.VERSION.SDK_INT)
                        put("device_id", Build.SERIAL.takeIf { it.isNotBlank() } ?: Build.ID)
                    }

                    // Send registration request
                    val result = NetworkClient.sendDeviceCommand("register", deviceInfo)

                    if (result.isSuccess) {
                        Log.i("AriaApp", "Device registered successfully with server.")
                        // Optionally save a local device ID returned by server
                        // prefs.edit().putInt("selected_device_id", serverDeviceId).apply()
                    } else {
                        Log.w("AriaApp", "Device registration failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (ex: Exception) {
                    Log.e("AriaApp", "Device registration error: ${ex.message}", ex)
                }
            }
        }
    }

    /**
     * Unregisters the device from the server. Can be called when user logs out.
     */
    fun unregisterDevice() {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val deviceId = prefs.getInt("selected_device_id", -1)
        if (deviceId != -1) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = NetworkClient.sendDeviceCommand("unregister", JSONObject().apply {
                        put("device_id", deviceId)
                    })

                    if (result.isSuccess) {
                        Log.i("AriaApp", "Device unregistered successfully from server.")
                        prefs.edit().remove("selected_device_id").apply()
                    } else {
                        Log.w("AriaApp", "Device unregistration failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (ex: Exception) {
                    Log.e("AriaApp", "Device unregistration error: ${ex.message}", ex)
                }
            }
        }
    }
}
