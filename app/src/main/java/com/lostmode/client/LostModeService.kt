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
 * LostModeService
 *
 * Handles ARIA's "Lost Mode" functionality:
 * - Locks device when commanded by server
 * - Sends location updates periodically
 * - Runs safely in foreground
 *
 * Updated to prevent immediate app exit and handle missing modes or permissions gracefully.
 */
class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val CHANNEL_ID = "lost_mode_channel"
        private const val CHANNEL_NAME = "ARIA System Services"
        private const val NOTIF_ID = 101
        private const val INTERVAL_MS = 10_000L
        private const val FASTEST_MS = 10_000L
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private var dpm: DevicePolicyManager? = null
    private var compName: ComponentName? = null
    private var lostModeActive = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")

        // Always run foreground service to prevent Android killing it
        startForeground(NOTIF_ID, buildNotification())

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)

        // Check if Lost Mode is required
        if (!requiresLostMode()) {
            Log.i(TAG, "Lost Mode not required — service running idle.")
            return
        }

        // Start location updates if permissions exist
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            Log.w(TAG, "Missing location permissions — updates will start after permission granted.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand")
        return START_STICKY
    }

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
            .setContentText("ARIA Lost Mode service running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    /**
     * Checks whether the app has all required location permissions.
     */
    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        val foreground = if (Build.VERSION.SDK_INT >= 34)
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        else PackageManager.PERMISSION_GRANTED

        return fine == PackageManager.PERMISSION_GRANTED &&
                coarse == PackageManager.PERMISSION_GRANTED &&
                foreground == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts periodic location updates.
     * Won't crash if permissions are missing — safe fallback.
     */
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted — cannot start updates.")
            return
        }

        val request: LocationRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        Log.i(TAG, "Location updates started.")
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location? = result.lastLocation
            if (loc != null && lostModeActive) {
                val mapLink = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                Log.d(TAG, "Lost Mode Location: ${loc.latitude},${loc.longitude} | $mapLink")
                NetworkClient.sendLocationToServer(loc) { json ->
                    try { handleServerCommands(json) } catch (_: Exception) { }
                }
            }
        }
    }

    /**
     * Handles server commands received via JSON.
     */
    private fun handleServerCommands(json: JSONObject) {
        if (!lostModeActive && json.optBoolean("lock", false)) {
            lostModeActive = true
            Log.i(TAG, "Lock command received from server.")
            lockDevice()
            if (hasLocationPermission()) startLocationUpdates()
        }
    }

    /**
     * Locks device if Device Admin is active.
     */
    private fun lockDevice() {
        if (dpm != null && compName != null && dpm!!.isAdminActive(compName!!)) {
            dpm!!.lockNow()
            Log.i(TAG, "Device locked by Lost Mode.")
        } else {
            Log.w(TAG, "Device admin not active — cannot lock.")
        }
    }

    override fun onDestroy() {
        try { fusedClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    /**
     * Returns true if user selected a mode that requires Lost Mode.
     * Safe fallback: if no mode is set, returns false without crashing.
     */
    private fun requiresLostMode(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val mode = prefs.getString("user_mode", "AI") // default = AI
        val required = mode == "SECURE" || mode == "BOTH"
        Log.i(TAG, "Mode check: user_mode=$mode | requiresLostMode=$required")
        return required
    }
}