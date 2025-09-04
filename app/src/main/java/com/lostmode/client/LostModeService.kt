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
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.json.JSONObject

/**
 * ARIA Lost Mode Foreground Service
 * Handles location updates and device locking.
 * Starts location updates only when server sends lock command.
 */
class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val CHANNEL_ID = "lost_mode_channel"
        private const val CHANNEL_NAME = "System Services"
        private const val NOTIF_ID = 101
        private const val INTERVAL_MS = 10_000L   // 10 seconds
        private const val FASTEST_MS = 10_000L
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var dpm: DevicePolicyManager
    private lateinit var compName: ComponentName
    private var locationUpdatesStarted = false

    override fun onCreate() {
        super.onCreate()
        try {
            Log.i(TAG, "Service onCreate")
            dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)
            fusedClient = LocationServices.getFusedLocationProviderClient(this)

            if (Build.VERSION.SDK_INT >= 34 &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Missing FOREGROUND_SERVICE_LOCATION permission — location updates will not start")
            }

            startForeground(NOTIF_ID, buildNotification())
        } catch (ex: Exception) {
            Log.e(TAG, "onCreate exception: ${ex.message}", ex)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                        description = "ARIA background service"
                    }
                )
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
        if (locationUpdatesStarted) return
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted — skipping updates.")
            return
        }

        val request: LocationRequest = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                LocationRequest.Builder(INTERVAL_MS)
                    .setMinUpdateIntervalMillis(FASTEST_MS)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .build()
            } else {
                LocationRequest.create().apply {
                    interval = INTERVAL_MS
                    fastestInterval = FASTEST_MS
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to build LocationRequest: ${ex.message}", ex)
            return
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            locationUpdatesStarted = true
            Log.i(TAG, "Location updates started (every 10s).")
        } catch (secEx: SecurityException) {
            Log.w(TAG, "SecurityException requesting location updates: ${secEx.message}")
        } catch (ex: Exception) {
            Log.e(TAG, "Exception requesting location updates: ${ex.message}", ex)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location? = result.lastLocation
            if (loc != null) {
                Log.d(TAG, "Got location: ${loc.latitude}, ${loc.longitude} (acc=${loc.accuracy})")
                // Send location with real-time map link
                val payload = JSONObject().apply {
                    put("lat", loc.latitude)
                    put("lng", loc.longitude)
                    put("accuracy", loc.accuracy)
                    put("timestamp", System.currentTimeMillis())
                    put("map_link", "https://www.google.com/maps?q=${loc.latitude},${loc.longitude}")
                }
                NetworkClient.sendLocationToServer(payload)
            }
        }
    }

    private fun handleServerCommand(json: JSONObject) {
        // Single-command logic
        if (json.optBoolean("lock", false)) {
            if (dpm.isAdminActive(compName)) {
                dpm.lockNow()
                Log.i(TAG, "Device locked by command.")
                startLocationUpdates()
            } else {
                Log.w(TAG, "Device admin not active — cannot lock.")
            }
        }
    }

    override fun onDestroy() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to remove location updates: ${ex.message}")
        }
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }
}