package com.lostmode.client

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.json.JSONObject
import java.lang.Exception

/**
 * Robust foreground service used by ARIA.
 * - Checks permissions before asking for location updates
 * - Uses modern LocationRequest.Builder on newer APIs
 * - Catches exceptions so the service doesn't crash
 * - Returns START_STICKY so system will try to restart it
 */
class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val CHANNEL_ID = "lost_mode_channel"
        private const val CHANNEL_NAME = "System Services"
        private const val NOTIF_ID = 101
        // location update intervals (ms)
        private const val INTERVAL_MS = 30_000L
        private const val FASTEST_MS = 15_000L
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var dpm: DevicePolicyManager
    private lateinit var compName: ComponentName
    private var lastRingtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        try {
            Log.i(TAG, "Service onCreate")
            dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)
            fusedClient = LocationServices.getFusedLocationProviderClient(this)

            // Build + start foreground immediately
            startForeground(NOTIF_ID, buildNotification())

            // Start location updates (only if permission available)
            startLocationUpdates()
        } catch (ex: Exception) {
            // We MUST not let exceptions bubble up and crash the service
            Log.e(TAG, "onCreate exception: ${ex.message}", ex)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        // Keep running (best-effort restart by system)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val chan = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "ARIA background service"
                }
                nm.createNotificationChannel(chan)
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Update")
            .setContentText("Service running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted — not requesting updates.")
            return
        }

        val request: LocationRequest = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Newer API: LocationRequest.Builder
                LocationRequest.Builder(INTERVAL_MS)
                    .setMinUpdateIntervalMillis(FASTEST_MS)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .build()
            } else {
                // Legacy API (works on older dependencies)
                LocationRequest.create().apply {
                    interval = INTERVAL_MS
                    fastestInterval = FASTEST_MS
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                }
            }
        } catch (ex: NoSuchMethodError) {
            // Fallback for older play services versions
            val legacy = LocationRequest.create()
            legacy.interval = INTERVAL_MS
            legacy.fastestInterval = FASTEST_MS
            legacy.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            legacy
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to build LocationRequest: ${ex.message}", ex)
            return
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            Log.i(TAG, "Location updates requested.")
        } catch (secEx: SecurityException) {
            // Shouldn't happen because we checked earlier — but handle gracefully
            Log.w(TAG, "SecurityException requesting location updates: ${secEx.message}")
        } catch (ex: Exception) {
            Log.e(TAG, "Exception requesting location updates: ${ex.message}", ex)
        }
    }

    // Correct signature: override fun onLocationResult(result: LocationResult)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            try {
                val loc: Location? = result.lastLocation
                if (loc == null) {
                    Log.w(TAG, "LocationResult has null lastLocation")
                    return
                }
                Log.d(TAG, "Got location: ${loc.latitude}, ${loc.longitude} (acc=${loc.accuracy})")

                // Send location to server — network is done async and is tolerant of failures
                NetworkClient.sendLocationToServer(loc) { json ->
                    // This callback may be invoked on an OKHttp worker thread — keep it safe
                    try {
                        handleServerCommands(json)
                    } catch (ex: Exception) {
                        Log.e(TAG, "handleServerCommands error: ${ex.message}", ex)
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "onLocationResult exception: ${ex.message}", ex)
            }
        }
    }

    /**
     * Parse server response and execute commands (lock/play sound)
     * Expected JSON keys:
     *  {"lock": true, "play_sound": true, "alert_text":"..."}
     */
    private fun handleServerCommands(json: JSONObject) {
        try {
            Log.d(TAG, "Server response: $json")
            if (json.optBoolean("lock", false)) {
                lockDevice()
            }
            if (json.optBoolean("play_sound", false)) {
                playAlertSound()
            }
            // Future: support stop_sound, set_password, sms_fallback, etc.
        } catch (ex: Exception) {
            Log.e(TAG, "handleServerCommands parse error: ${ex.message}", ex)
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
            Log.e(TAG, "Failed to lock device: ${ex.message}", ex)
        }
    }

    private fun playAlertSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            stopAlertSound()
            lastRingtone = RingtoneManager.getRingtone(applicationContext, uri)
            lastRingtone?.play()
            Log.i(TAG, "Playing alert ringtone.")
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to play ringtone: ${ex.message}", ex)
        }
    }

    private fun stopAlertSound() {
        try {
            lastRingtone?.let {
                if (it.isPlaying) it.stop()
            }
            lastRingtone = null
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to stop ringtone: ${ex.message}", ex)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to remove location updates: ${ex.message}")
        }
        stopAlertSound()
        Log.i(TAG, "Service destroyed")
    }
}