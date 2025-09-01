package com.lostmode.client

/**
 * Update SERVER_BASE to the IP shown by your Pydroid server (e.g. http://192.168.1.35:5000)
 * The client will POST location updates to SERVER_BASE + "/update"
 */
object Config {
    // Example: "http://192.168.1.35:5000"
    const val SERVER_BASE = "http://192.168.1.102:5000"
    const val UPDATE_ENDPOINT = "$SERVER_BASE/update"
}