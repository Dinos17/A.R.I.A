package com.lostmode.client

import android.location.Location
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val TAG = "NetworkClient"
    private const val PREFS_NAME = "ARIA_PREFS"
    private const val PREFS_TOKEN_KEY = "auth_token"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    // --- Data class for devices ---
    data class Device(
        val id: Int,
        val name: String,
        val owner_id: Int,
        val last_lat: Double,
        val last_lng: Double,
        val last_update: String
    )

    // --- Token Management ---
    private fun getToken(): String? {
        val prefs = AriaApp.instance.getSharedPreferences(
            PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
        return prefs.getString(PREFS_TOKEN_KEY, null)
    }

    private fun saveToken(token: String) {
        val prefs = AriaApp.instance.getSharedPreferences(
            PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
        prefs.edit().putString(PREFS_TOKEN_KEY, token).apply()
        Log.i(TAG, "Auth token saved")
    }

    private fun buildRequest(url: String, body: RequestBody? = null): Request.Builder {
        val builder = Request.Builder().url(url)
        getToken()?.let { builder.addHeader("Authorization", "Bearer $it") }
        body?.let { builder.post(it) }
        return builder
    }

    // --- AUTHENTICATION ---
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

        client.newCall(Request.Builder().url(endpoint).post(body).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Auth request failed: ${e.message}", e)
                    onResult(false, "Network error: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val respStr = it.body?.string()
                        Log.i(TAG, "Auth response: HTTP ${it.code}, body=$respStr")
                        if (it.isSuccessful && !respStr.isNullOrEmpty()) {
                            try {
                                val json = JSONObject(respStr)
                                val token = json.optString("token", "")
                                if (token.isNotEmpty()) {
                                    saveToken(token)
                                    onResult(true, null)
                                } else {
                                    onResult(false, json.optString("message", "Invalid response"))
                                }
                            } catch (ex: Exception) {
                                Log.e(TAG, "Auth JSON parse error: ${ex.message}", ex)
                                onResult(false, "Parsing error")
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

    // --- LOCATION UPDATES ---
    fun sendLocationToServer(location: Location, onCommands: ((JSONObject) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", location.accuracy)
            put("timestamp", System.currentTimeMillis())
            put("map_link", "https://maps.google.com/?q=${location.latitude},${location.longitude}")
        }
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        client.newCall(buildRequest(Config.UPDATE_ENDPOINT, body).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Location send failed: ${e.message}", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val respStr = it.body?.string()
                        Log.i(TAG, "Location response: HTTP ${it.code}, body=$respStr")
                        if (it.isSuccessful && !respStr.isNullOrEmpty()) {
                            try {
                                onCommands?.invoke(JSONObject(respStr))
                            } catch (ex: Exception) {
                                Log.e(TAG, "Command parse error: ${ex.message}", ex)
                            }
                        }
                    }
                }
            })
    }

    // --- DEVICES ---
    fun fetchDevices(onResult: (List<Device>?) -> Unit) {
        client.newCall(buildRequest(Config.DEVICES_ENDPOINT).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Fetch devices failed: ${e.message}", e)
                    onResult(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val respStr = it.body?.string()
                        Log.i(TAG, "Fetch devices response: HTTP ${it.code}, body=$respStr")
                        if (!it.isSuccessful || respStr.isNullOrEmpty()) {
                            onResult(null)
                            return
                        }
                        try {
                            val deviceListType = object : TypeToken<List<Device>>() {}.type
                            val devices: List<Device> = gson.fromJson(respStr, deviceListType)
                            onResult(devices)
                        } catch (ex: Exception) {
                            Log.e(TAG, "Device JSON parse error: ${ex.message}", ex)
                            onResult(null)
                        }
                    }
                }
            })
    }

    // --- DEVICE COMMANDS ---
    fun sendDeviceCommand(deviceId: String, command: JSONObject, onResult: ((Boolean) -> Unit)? = null) {
        val body = command.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest("${Config.COMMAND_ENDPOINT}/$deviceId", body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Device command failed: ${e.message}", e)
                onResult?.invoke(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult?.invoke(it.isSuccessful)
                    if (!it.isSuccessful) Log.w(TAG, "Device command failed: HTTP ${it.code}")
                }
            }
        })
    }
}