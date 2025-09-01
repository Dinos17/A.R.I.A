package com.lostmode.client

import android.location.Location
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object NetworkClient {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Send location to server asynchronously.
     * onCommands will be invoked with the server JSON response (if any).
     */
    fun sendLocationToServer(location: Location, onCommands: ((JSONObject) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", location.accuracy)
            put("timestamp", System.currentTimeMillis())
        }

        val body = payload.toString().toRequestBody(JSON)
        val req = Request.Builder()
            .url(Config.UPDATE_ENDPOINT)
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // network failure — optionally log
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string()
                    if (!text.isNullOrEmpty()) {
                        try {
                            val json = JSONObject(text)
                            onCommands?.invoke(json)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            }
        })
    }
}