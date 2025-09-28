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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.json.JSONObject

class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val CHANNEL_ID = "lost_mode_channel"
        private const val CHANNEL_NAME = "ARIA System Service"
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
        Log.i(TAG, "Service created")

        startForeground(NOTIF_ID, buildNotification())

        if (!requiresLostMode()) {
            Log.i(TAG, "Lost Mode not required, service idle")
            Toast.makeText(this, "Lost Mode not required", Toast.LENGTH_SHORT).show()
            return
        }

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            Log.w(TAG, "Missing location permissions")
            Toast.makeText(this, "Location permissions missing", Toast.LENGTH_LONG).show()
        }

        Log.i(TAG, "LostModeService initialized successfully")
        Toast.makeText(this, "Lost Mode Service started", Toast.LENGTH_SHORT).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand")
        Toast.makeText(this, "Lost Mode Service running", Toast.LENGTH_SHORT).show()
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
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted — skipping updates")
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
        Log.i(TAG, "Location updates requested")
        Toast.makeText(this, "Starting location updates...", Toast.LENGTH_SHORT).show()
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location? = result.lastLocation
            if (loc != null) {
                val mapLink = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                Log.d(TAG, "Location: ${loc.latitude}, ${loc.longitude} | $mapLink")
                Toast.makeText(this@LostModeService, "Location updated", Toast.LENGTH_SHORT).show()

                NetworkClient.sendLocationToServer(loc) { json ->
                    try {
                        handleServerCommands(json)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error handling server commands: ${ex.message}", ex)
                    }
                }
            }
        }
    }

    private fun handleServerCommands(json: JSONObject) {
        if (!lostModeActive && json.optBoolean("lock", false)) {
            lostModeActive = true
            Log.i(TAG, "Server requested device lock")
            Toast.makeText(this, "Activating Lost Mode...", Toast.LENGTH_SHORT).show()
            lockDevice()
            startLocationUpdates()
        }
    }

    private fun lockDevice() {
        if (dpm != null && compName != null && dpm!!.isAdminActive(compName!!)) {
            dpm!!.lockNow()
            Log.i(TAG, "Device locked by Lost Mode")
            Toast.makeText(this, "Device locked!", Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "Device admin not active — cannot lock")
            Toast.makeText(this, "Device admin not active", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        try { fusedClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        Log.i(TAG, "LostModeService destroyed")
        Toast.makeText(this, "Lost Mode Service stopped", Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }

    private fun requiresLostMode(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val mode = prefs.getString("user_mode", "AI")
        return mode == "SECURE" || mode == "BOTH"
    }
}
