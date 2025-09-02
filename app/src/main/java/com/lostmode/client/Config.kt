package com.lostmode.client

/**
 * Configuration for ARIA Client.
 *
 * SERVER_BASE: Update this to your PC/server IP address or domain.
 * Example: "http://192.168.1.147:5000" or "https://myserver.com"
 *
 * UPDATE_ENDPOINT: API endpoint to send location updates.
 * COMMAND_ENDPOINT: API endpoint to fetch server commands.
 *
 * This object is designed for Android 14+ compatibility and thread-safe access.
 */
object Config {

    // Base server URL (update to match your server)
    const val SERVER_BASE: String = "http://192.168.1.147:5000"

    // Endpoint to send device location updates
    val UPDATE_ENDPOINT: String
        get() = "$SERVER_BASE/update"

    // Endpoint to fetch server commands
    val COMMAND_ENDPOINT: String
        get() = "$SERVER_BASE/command"

    // Optional: configurable timeout or retry parameters
    const val NETWORK_TIMEOUT_MS: Long = 15_000L
    const val MAX_RETRY_ATTEMPTS: Int = 3
}