package com.lostmode.client

import android.location.Location
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Handles all networking for the ARIA Lost Mode Client.
 * Provides public access to the OkHttpClient for other classes.
 */
object NetworkClient {

    private const val TAG = "NetworkClient"

    // Public client so DeviceListActivity can access it
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // --- Helper to get JWT token ---
    private fun getToken(): String? {
        val prefs = AriaApp.instance
            .getSharedPreferences("ARIA_PREFS", android.content.Context.MODE_PRIVATE)
        return prefs.getString("user_token", null)
    }

    private fun buildRequest(url: String, body: RequestBody? = null): Request.Builder {
        val builder = Request.Builder().url(url)
        val token = getToken()
        if (token != null) builder.addHeader("Authorization", "Bearer $token")
        if (body != null) builder.post(body)
        return builder
    }

    /**
     * Send location to server asynchronously.
     */
    fun sendLocationToServer(location: Location, onCommands: ((JSONObject) -> Unit)? = null) {
        val mapLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        val payload = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", location.accuracy)
            put("timestamp", System.currentTimeMillis())
            put("map_link", mapLink)
        }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest(Config.UPDATE_ENDPOINT, body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send location: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.w(TAG, "Unexpected response code: ${it.code}")
                        return
                    }

                    val responseBody = it.body?.string()
                    if (!responseBody.isNullOrEmpty()) {
                        try {
                            val json = JSONObject(responseBody)
                            onCommands?.invoke(json)
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to parse server JSON: ${ex.message}", ex)
                        }
                    } else {
                        Log.w(TAG, "Server returned empty response")
                    }
                }
            }
        })
    }

    /**
     * Fetch devices list linked to this account.
     */
    fun fetchDevices(onResult: (JSONArray?) -> Unit) {
        val request = buildRequest(Config.DEVICES_ENDPOINT).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch devices: ${e.message}", e)
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.w(TAG, "Unexpected response code: ${it.code}")
                        onResult(null)
                        return
                    }
                    val responseBody = it.body?.string()
                    if (!responseBody.isNullOrEmpty()) {
                        try {
                            val json = JSONArray(responseBody)
                            onResult(json)
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to parse devices JSON: ${ex.message}", ex)
                            onResult(null)
                        }
                    } else {
                        onResult(null)
                    }
                }
            }
        })
    }

    /**
     * Send command to a specific device.
     */
    fun sendDeviceCommand(
        deviceId: String,
        command: JSONObject,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val body = command.toString().toRequestBody(JSON_MEDIA_TYPE)
        val url = "${Config.COMMAND_ENDPOINT}/$deviceId"
        val request = buildRequest(url, body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send device command: ${e.message}", e)
                onResult?.invoke(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult?.invoke(it.isSuccessful)
                }
            }
        })
    }
}
