package com.lostmode.client

import android.location.Location
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NetworkClient {

    private const val SERVER_URL = "http://YOUR_PC_IP:5000/update_location"

    fun sendLocationToServer(location: Location) {
        Thread {
            try {
                val url = URL("$SERVER_URL?lat=${location.latitude}&lng=${location.longitude}")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connect()
                conn.inputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
