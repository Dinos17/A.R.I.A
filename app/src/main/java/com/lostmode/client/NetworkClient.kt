package com.lostmode.client

import android.location.Location
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Central networking client for ARIA Lost Mode Client.
 * Handles authentication, location updates, device commands, and devices list retrieval.
 */
object NetworkClient {

    private const val TAG = "NetworkClient"
    private const val PREFS_NAME = "aria_prefs"
    private const val PREFS_TOKEN_KEY = "auth_token"

    // Shared OkHttpClient with timeout and retry configured
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // --- Token management ---
    private fun getToken(): String? {
        val prefs = AriaApp.instance.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getString(PREFS_TOKEN_KEY, null)
    }

    private fun saveToken(token: String) {
        val prefs = AriaApp.instance.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_TOKEN_KEY, token).apply()
        Log.i(TAG, "Auth token saved successfully.")
    }

    private fun buildRequest(url: String, body: RequestBody? = null): Request.Builder {
        val builder = Request.Builder().url(url)
        getToken()?.let { builder.addHeader("Authorization", "Bearer $it") }
        body?.let { builder.post(it) }
        return builder
    }

    // --- AUTH (Login & Signup) ---
    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        sendAuthRequest(Config.LOGIN_ENDPOINT, email, password, onResult)
    }

    fun signup(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        sendAuthRequest(Config.SIGNUP_ENDPOINT, email, password, onResult)
    }

    private fun sendAuthRequest(
        endpoint: String,
        email: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val payload = JSONObject().apply {
            put("email", email)
            put("password", password)
        }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(endpoint).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Auth request failed: ${e.message}", e)
                onResult(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val respStr = it.body?.string()
                    if (it.isSuccessful && !respStr.isNullOrEmpty()) {
                        try {
                            val json = JSONObject(respStr)
                            val token = json.optString("token", "")
                            if (token.isNotEmpty()) {
                                saveToken(token)
                                onResult(true, null)
                            } else {
                                onResult(false, json.optString("message", "Invalid auth response"))
                            }
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to parse auth response: ${ex.message}", ex)
                            onResult(false, "Response parsing error")
                        }
                    } else {
                        val msg = try {
                            JSONObject(respStr ?: "{}").optString("message", "Auth failed")
                        } catch (_: Exception) {
                            "Auth failed"
                        }
                        onResult(false, msg)
                    }
                }
            }
        })
    }

    // --- LOCATION ---
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
                        Log.w(TAG, "Location update failed: HTTP ${it.code}")
                        return
                    }
                    val respStr = it.body?.string()
                    if (!respStr.isNullOrEmpty()) {
                        try {
                            val json = JSONObject(respStr)
                            onCommands?.invoke(json)
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to parse server commands JSON: ${ex.message}", ex)
                        }
                    } else {
                        Log.w(TAG, "Server returned empty location response")
                    }
                }
            }
        })
    }

    // --- DEVICES LIST ---
    fun fetchDevices(onResult: (JSONArray?) -> Unit) {
        val request = buildRequest(Config.DEVICES_ENDPOINT).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Fetch devices failed: ${e.message}", e)
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.w(TAG, "Fetch devices failed: HTTP ${it.code}")
                        onResult(null)
                        return
                    }
                    val respStr = it.body?.string()
                    if (!respStr.isNullOrEmpty()) {
                        try {
                            val json = JSONArray(respStr)
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

    // --- DEVICE COMMANDS ---
    fun sendDeviceCommand(deviceId: String, command: JSONObject, onResult: ((Boolean) -> Unit)? = null) {
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
                    if (!it.isSuccessful) {
                        Log.w(TAG, "Device command failed: HTTP ${it.code}")
                    }
                }
            }
        })
    }
}