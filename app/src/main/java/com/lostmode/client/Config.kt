package com.lostmode.client

/**
 * Configuration for ARIA Client.
 * 
 * SERVER_BASE: Update this to your PC/server IP address.
 * Example: "http://192.168.1.147:5000"
 *
 * UPDATE_ENDPOINT: API endpoint to send location updates.
 * COMMAND_ENDPOINT: API endpoint to fetch server commands.
 */
object Config {
    const val SERVER_BASE = "http://192.168.1.147:5000"
    
    // Endpoint to send device location updates
    const val UPDATE_ENDPOINT = "$SERVER_BASE/update"
    
    // Endpoint to fetch commands from server
    const val COMMAND_ENDPOINT = "$SERVER_BASE/command"
}