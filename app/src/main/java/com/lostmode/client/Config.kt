package com.lostmode.client

/**
 * Configuration for ARIA Client.
 *
 * SERVER_BASE: Update this to your server IP address or domain.
 * Example: "http://192.168.1.147:5000" or "https://myserver.com"
 *
 * Contains endpoints for location updates, device commands, devices list, authentication,
 * and device registration/unregistration.
 */
object Config {

    // Base server URL
    const val SERVER_BASE: String = "http://192.168.1.147:5000"

    // SharedPreferences key for auth token
    const val PREFS_AUTH_TOKEN = "auth_token"

    // Endpoint to send device location updates
    val UPDATE_ENDPOINT: String
        get() = "$SERVER_BASE/api/update_location"

    // Endpoint to send commands to a specific device
    val COMMAND_ENDPOINT: String
        get() = "$SERVER_BASE/api/device_command"

    // Endpoint to fetch devices list for the logged-in account
    val DEVICES_ENDPOINT: String
        get() = "$SERVER_BASE/api/devices"

    // Endpoint to register a new device
    val ADD_DEVICE_ENDPOINT: String
        get() = "$SERVER_BASE/api/register_device"

    // Endpoint to unregister a device (future implementation)
    val REMOVE_DEVICE_ENDPOINT: String
        get() = "$SERVER_BASE/api/unregister_device"

    // Endpoint for login
    val LOGIN_ENDPOINT: String
        get() = "$SERVER_BASE/api/login"

    // Endpoint for signup
    val SIGNUP_ENDPOINT: String
        get() = "$SERVER_BASE/api/signup"

    // Optional network parameters
    const val NETWORK_TIMEOUT_MS: Long = 15_000L
    const val MAX_RETRY_ATTEMPTS: Int = 3
}
