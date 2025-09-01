package com.lostmode.client

object Config {
    // Your server IP or domain (change as needed)
    const val SERVER_IP = "192.168.1.100"  // replace with your old phone/server IP
    const val SERVER_PORT = 5000           // optional, if your server uses a specific port

    // Full endpoint for sending location updates
    val UPDATE_ENDPOINT: String
        get() = "http://$SERVER_IP:$SERVER_PORT/update_location"
}