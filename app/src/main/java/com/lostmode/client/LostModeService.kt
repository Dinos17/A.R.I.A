package com.lostmode.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val CHANNEL_ID = "lost_mode_channel"
        private const val NOTIF_ID = 101
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var dpm: DevicePolicyManager
    private lateinit var compName: ComponentName
    private var lastRingtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        startForeground(NOTIF_ID, buildNotification())
        startLocationUpdates()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "System Services",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(chan)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Update")
            .setContentText("Service running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun startLocationUpdates() {
        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // New builder API (preferred)
            LocationRequest.Builder(30_000)
                .setMinUpdateIntervalMillis(15_000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()
        } else {
            // Fallback for older API (may be deprecated but safe)
            LocationRequest.create().apply {
                interval = 30_000
                fastestInterval = 15_000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (ex: SecurityException) {
            Log.w(TAG, "Missing location permission: ${ex.message}")
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            val loc: Location? = result?.lastLocation
            loc?.let {
                // Send to server and handle returned commands (if any)
                NetworkClient.sendLocationToServer(it) { json ->
                    handleServerCommands(json)
                }
            }
        }
    }

    private fun handleServerCommands(json: org.json.JSONObject) {
        // Expected server keys: lock (boolean), play_sound (boolean), alert_text (string)
        try {
            if (json.optBoolean("lock", false)) {
                lockDevice()
            }
            if (json.optBoolean("play_sound", false)) {
                playAlertSound()
            }
            // future: parse more commands here
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun lockDevice() {
        try {
            if (dpm.isAdminActive(compName)) {
                dpm.lockNow()
                Log.i(TAG, "Device locked by command.")
            } else {
                Log.w(TAG, "Device admin not active — cannot lock.")
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun playAlertSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            stopAlertSound() // stop previous if playing
            lastRingtone = RingtoneManager.getRingtone(applicationContext, uri)
            lastRingtone?.play()
            Log.i(TAG, "Playing alert ringtone.")
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun stopAlertSound() {
        try {
            lastRingtone?.let {
                if (it.isPlaying) it.stop()
            }
            lastRingtone = null
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        stopAlertSound()
    }
}