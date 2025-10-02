package com.lostmode.client

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val TAG = "NetworkClient"
    private const val PREFS_NAME = "ARIA_PREFS"
    private const val PREFS_TOKEN_KEY = "auth_token"

    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // --- OkHttp Client with Logging ---
    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message -> Log.d(TAG, message) }
        logging.level = HttpLoggingInterceptor.Level.BODY

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(Config.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // --- Device data class ---
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
        val prefs = AriaApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREFS_TOKEN_KEY, null)
    }

    private fun saveToken(token: String) {
        val prefs = AriaApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_TOKEN_KEY, token).apply()
        Log.i(TAG, "Auth token saved")
    }

    private fun buildRequest(url: String, body: RequestBody? = null): Request.Builder {
        val builder = Request.Builder().url(url)
        getToken()?.let { builder.addHeader("Authorization", "Bearer $it") }
        body?.let { builder.post(it) }
        return builder
    }

    // --- Authentication ---
    suspend fun login(email: String, password: String): Result<String> =
        sendAuthRequest(Config.LOGIN_ENDPOINT, email, password)

    suspend fun signup(email: String, password: String): Result<String> =
        sendAuthRequest(Config.SIGNUP_ENDPOINT, email, password)

    private suspend fun sendAuthRequest(endpoint: String, email: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }
                val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder().url(endpoint).post(body).build()
                client.newCall(request).execute().use { response ->
                    val respStr = response.body?.string()
                    Log.i(TAG, "Auth response: HTTP ${response.code}, body=$respStr")
                    if (!response.isSuccessful || respStr.isNullOrEmpty()) {
                        return@withContext Result.failure(Exception("Auth failed: HTTP ${response.code}"))
                    }
                    val json = JSONObject(respStr)
                    val token = json.optString("token", "")
                    return@withContext if (token.isNotEmpty()) {
                        saveToken(token)
                        Result.success(token)
                    } else {
                        Result.failure(Exception(json.optString("message", "Invalid response")))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auth error", e)
                Result.failure(e)
            }
        }
    }

    // --- Device Registration ---
    suspend fun registerDevice(deviceName: String): Result<Device> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("name", deviceName)
                put("last_lat", JSONObject.NULL)
                put("last_lng", JSONObject.NULL)
                put("last_update", JSONObject.NULL)
            }
            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = buildRequest(Config.ADD_DEVICE_ENDPOINT, body).build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string()
                Log.i(TAG, "Device registration response: HTTP ${response.code}, body=$respStr")
                if (!response.isSuccessful || respStr.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Device registration failed: HTTP ${response.code}"))
                }
                val json = JSONObject(respStr)
                val device = Device(
                    id = json.getInt("id"),
                    name = json.getString("name"),
                    owner_id = json.getInt("owner_id"),
                    last_lat = json.optDouble("last_lat", 0.0),
                    last_lng = json.optDouble("last_lng", 0.0),
                    last_update = json.optString("last_update", "")
                )

                // Save device ID locally
                val prefs = AriaApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putInt("selected_device_id", device.id).apply()

                return@withContext Result.success(device)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Device registration error", e)
            Result.failure(e)
        }
    }

    // --- Location Updates ---
    suspend fun sendLocationToServer(location: Location): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val prefs = AriaApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val deviceId = prefs.getInt("selected_device_id", -1)
            val payload = JSONObject().apply {
                if (deviceId > 0) put("device_id", deviceId)
                put("lat", location.latitude)
                put("lng", location.longitude)
                put("accuracy", location.accuracy)
                put("timestamp", System.currentTimeMillis())
                put("map_link", "https://maps.google.com/?q=${location.latitude},${location.longitude}")
            }
            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = buildRequest(Config.UPDATE_ENDPOINT, body).build()
            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string()
                Log.i(TAG, "Location response: HTTP ${response.code}, body=$respStr")
                if (response.isSuccessful && !respStr.isNullOrEmpty()) {
                    return@withContext Result.success(JSONObject(respStr))
                }
                return@withContext Result.failure(Exception("Location send failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location send error", e)
            Result.failure(e)
        }
    }

    // --- Devices ---
    suspend fun fetchDevices(): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(Config.DEVICES_ENDPOINT).build()
            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string()
                Log.i(TAG, "Fetch devices response: HTTP ${response.code}, body=$respStr")
                if (!response.isSuccessful || respStr.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Fetch devices failed: HTTP ${response.code}"))
                }
                val typeToken = object : TypeToken<List<Device>>() {}.type
                val devices: List<Device> = gson.fromJson(respStr, typeToken)
                Result.success(devices)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch devices error", e)
            Result.failure(e)
        }
    }

    // --- Device Commands ---
    suspend fun sendDeviceCommand(deviceId: String, command: JSONObject): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = command.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = buildRequest("${Config.COMMAND_ENDPOINT}/$deviceId", body).build()
            client.newCall(request).execute().use { response ->
                Log.i(TAG, "Device command response: HTTP ${response.code}")
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Device command error", e)
            Result.failure(e)
        }
    }
}
