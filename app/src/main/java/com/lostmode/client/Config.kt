package com.lostmode.client

/**
 * Configuration for ARIA Client.
 *
 * Update SERVER_BASE to the IP shown by your Pydroid server.
 * Example: http://192.168.1.35:5000
 *
 * The client will POST location updates to SERVER_BASE + "/update".
 */
object Config {
    // Current server base URL (your Flask server on Pydroid)
    const val SERVER_BASE = "http://192.168.1.102:5000"

    // API endpoint for location updates
    const val UPDATE_ENDPOINT = "$SERVER_BASE/update"
}