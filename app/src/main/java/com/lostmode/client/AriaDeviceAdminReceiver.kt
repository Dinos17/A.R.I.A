package com.lostmode.client

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * DeviceAdminReceiver for ARIA Lost Mode.
 * Handles device admin events and logs them for debugging.
 */
class AriaDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AriaDeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled. Lost Mode can now lock the device.")
        // Optional: notify service to re-check admin and perform any pending actions
        // Intent to service could be triggered here if needed
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device admin disabled. Lost Mode functionality limited.")
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.i(TAG, "Device password changed successfully.")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "Device password attempt failed. Possible unauthorized access.")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.i(TAG, "Device password attempt succeeded.")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "DeviceAdminReceiver received intent: ${intent.action}")
    }
}
