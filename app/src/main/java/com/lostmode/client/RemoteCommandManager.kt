package com.lostmode.client

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.app.admin.DevicePolicyManager
import android.content.ComponentName

object RemoteCommandManager {

    fun processCommand(context: Context, command: String) {
        when (command.trim().lowercase()) {
            "lock" -> lockDevice(context)
            "sound" -> playAlarm()
            // "wipe" -> wipeDevice(context) // implement with caution
            else -> { /* unknown */ }
        }
    }

    private fun lockDevice(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(context, AriaDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(compName)) {
            dpm.lockNow()
        }
    }

    private fun playAlarm() {
        // short beep sequence
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
            handler.postDelayed({ tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700) }, 900)
            handler.postDelayed({ tone.release() }, 2000)
        }
    }
}