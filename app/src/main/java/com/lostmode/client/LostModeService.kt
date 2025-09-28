package com.lostmode.client

import android.Manifest
import android.app.*
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
        startForeground(NOTIF_ID, buildNotification())

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)

        if (!requiresLostMode()) {
            Log.i(TAG, "Lost Mode not required — service idle.")
            return
        }

        if (dpm?.isAdminActive(compName!!) == true) {
            lockDevice()
            lostModeActive = true
        }

        if (hasLocationPermission()) startLocationUpdates()
        else Log.w(TAG, "Missing location permission — waiting to start updates.")
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
                        description = "ARIA Lost Mode foreground service"
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

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return

        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
            val loc = result.lastLocation ?: return
            if (!lostModeActive) return

            Log.d(TAG, "Lost Mode Location: ${loc.latitude},${loc.longitude}")
            NetworkClient.sendLocationToServer(loc) { json ->
                handleServerCommands(json)
            }
        }
    }

    private fun handleServerCommands(json: JSONObject) {
        if (!lostModeActive && json.optBoolean("lock", false)) {
            lostModeActive = true
            lockDevice()
            if (hasLocationPermission()) startLocationUpdates()
        }
    }

    private fun lockDevice() {
        if (dpm?.isAdminActive(compName!!) == true) {
            dpm!!.lockNow()
            Log.i(TAG, "Device locked by Lost Mode.")
        } else {
            Log.w(TAG, "Device admin inactive — cannot lock.")
        }
    }

    override fun onDestroy() {
        try { fusedClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun requiresLostMode(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val mode = prefs.getString("user_mode", "AI")
        val required = mode == "SECURE" || mode == "BOTH"
        Log.i(TAG, "Mode check: user_mode=$mode | requiresLostMode=$required")
        return required
    }
}