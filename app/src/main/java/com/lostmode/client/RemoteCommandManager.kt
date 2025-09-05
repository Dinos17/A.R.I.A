package com.lostmode.client

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject

object RemoteCommandManager {

    private const val TAG = "RemoteCommandManager"

    /**
     * Process command received from server for this device.
     */
    fun processServerCommand(context: Context, json: JSONObject) {
        try {
            if (json.optBoolean("lock", false)) lockDevice(context)
            if (json.optBoolean("sound", false)) playAlarm()
            // Can add more commands in future (e.g., "disable_features")
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to process server command: ${ex.message}", ex)
        }
    }

    /**
     * Send a command to another device by its ID.
     */
    fun sendCommandToDevice(deviceId: String, command: JSONObject, onResult: ((Boolean) -> Unit)? = null) {
        NetworkClient.sendDeviceCommand(deviceId, command, onResult)
    }

    // --- Helpers ---
    private fun lockDevice(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(context, AriaDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(compName)) {
            dpm.lockNow()
            Log.i(TAG, "Device locked by RemoteCommandManager.")
        } else {
            Log.w(TAG, "Device admin not active — cannot lock.")
        }
    }

    private fun playAlarm() {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
            handler.postDelayed({ tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700) }, 900)
            handler.postDelayed({ tone.release() }, 2000)
        }
    }
}
