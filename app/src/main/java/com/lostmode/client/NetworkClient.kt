package com.lostmode.client

import android.location.Location
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object NetworkClient {

    private const val TAG = "NetworkClient"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Send location to server asynchronously.
     * Adds a Google Maps link for real-time tracking.
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
        val request = Request.Builder()
            .url(Config.UPDATE_ENDPOINT)
            .post(body)
            .build()

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
}