package com.lostmode.client

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object NetworkClient {
    // TODO: Replace with your server endpoint. Use HTTPS in production.
    private const val SERVER_URL = "http://192.168.1.102:5000/receive_location"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun sendLocation(lat: Double, lon: Double) {
        try {
            // A tiny "encryption-like" wrapper — replace with real encryption if needed
            val payload = "{\"lat\":$lat,\"lon\":$lon}"

            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val req = Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                // no-op (you can log or handle)
            }
        } catch (e: Exception) {
            // swallow for now; service will retry/resend via other means
        }
    }
}