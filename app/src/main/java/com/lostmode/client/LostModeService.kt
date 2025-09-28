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
import java.lang.Exception

/**
 * LostModeService
 *
 * Foreground service responsible for:
 *  - ensuring device is locked when Device Admin is active,
 *  - collecting location updates while Lost Mode is active,
 *  - sending locations to server via NetworkClient,
 *  - reacting to server commands (e.g. lock).
 *
 * This service is defensive:
 *  - it checks permissions before starting location updates,
 *  - it logs and keeps running where appropriate (START_STICKY),
 *  - it exposes a notification that sends users back to the app to resolve missing permissions/admin.
 */
class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val CHANNEL_ID = "lost_mode_channel"
        private const val CHANNEL_NAME = "ARIA System Services"
        private const val NOTIF_ID = 101

        // Location update timing
        private const val INTERVAL_MS = 10_000L
        private const val FASTEST_MS = 5_000L
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private var dpm: DevicePolicyManager? = null
    private var compName: ComponentName? = null

    // Whether Lost Mode has taken effect (locked device at least once)
    private var lostModeActive = false

    // Location callback
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location = result.lastLocation ?: return
            if (!lostModeActive) {
                // If lostModeActive false, still send location if requiresLostMode? We require explicit activation to actively act.
                Log.d(TAG, "Received location but Lost Mode not active. Dropping or sending depending on policy.")
            }
            try {
                Log.d(TAG, "Location: ${loc.latitude}, ${loc.longitude}")
                // Send location to server. NetworkClient.sendLocationToServer should accept Location and callback.
                NetworkClient.sendLocationToServer(loc) { json ->
                    try {
                        handleServerCommands(json)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error handling server commands", ex)
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error sending location to server", ex)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")

        // Build notification channel + start foreground immediately (required on modern Android)
        try {
            startForeground(NOTIF_ID, buildNotification(isProblem = false))
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to start foreground notification", ex)
            // If we cannot start as foreground, there's little we can do - stop service to avoid being killed silently
            stopSelf()
            return
        }

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)

        // If Lost Mode not required by prefs, stop the service gracefully.
        if (!requiresLostMode()) {
            Log.i(TAG, "Lost Mode not required by preferences -> stopping service.")
            stopSelf()
            return
        }

        // Attempt to lock device if admin is active.
        try {
            if (compName != null && dpm?.isAdminActive(compName!!) == true) {
                lockDevice()
                lostModeActive = true
            } else {
                Log.w(TAG, "Device admin not active. Device will not be locked until admin is granted.")
                // Update notification to inform user they must enable Device Admin
                updateNotificationProblem("Device admin inactive — tap to enable.")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error checking device admin / locking", ex)
        }

        // Start location updates only if permissions present. If not, update the notification to instruct user.
        if (hasAllRequiredLocationPermissions()) {
            startLocationUpdates()
        } else {
            Log.w(TAG, "Missing location permissions; not starting location updates.")
            updateNotificationProblem("Missing location permissions — tap to grant.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        // If someone explicitly asked to re-check (via Intent extras) we could re-evaluate here.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------
    // Permission checks
    // -------------------------
    private fun hasAllRequiredLocationPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        val backgroundOk = if (Build.VERSION.SDK_INT >= 29) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        val fgServiceLocOk = if (Build.VERSION.SDK_INT >= 34) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        return fine && coarse && backgroundOk && fgServiceLocOk
    }

    // -------------------------
    // Location updates
    // -------------------------
    private fun startLocationUpdates() {
        if (!hasAllRequiredLocationPermissions()) {
            Log.w(TAG, "startLocationUpdates aborted: missing permissions")
            return
        }

        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocationRequest.Builder(INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_MS)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()
        } else {
            @Suppress("DEPRECATION")
            LocationRequest.create().apply {
                interval = INTERVAL_MS
                fastestInterval = FASTEST_MS
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            Log.i(TAG, "Location updates requested")
            // If we were showing a "missing permissions" problem, restore normal notification
            updateNotificationNormal()
        } catch (ex: SecurityException) {
            Log.e(TAG, "SecurityException starting location updates; permissions missing", ex)
            updateNotificationProblem("Missing location permissions — tap to grant.")
        } catch (ex: Exception) {
            Log.e(TAG, "Exception requesting location updates", ex)
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
            Log.i(TAG, "Location updates stopped")
        } catch (ex: Exception) {
            Log.w(TAG, "Error removing location updates", ex)
        }
    }

    // -------------------------
    // Server command handling
    // -------------------------
    private fun handleServerCommands(json: JSONObject?) {
        if (json == null) return
        try {
            if (json.optBoolean("lock", false)) {
                Log.i(TAG, "Server requested lock -> locking device")
                if (dpm?.isAdminActive(compName!!) == true) {
                    lockDevice()
                    lostModeActive = true
                } else {
                    Log.w(TAG, "Server requested lock but Device Admin is inactive")
                    updateNotificationProblem("Server requested lock but admin inactive — tap to enable.")
                }
            }
            // Add other command handlers here (e.g., wipe, ring, message)
        } catch (ex: Exception) {
            Log.e(TAG, "Error parsing server commands", ex)
        }
    }

    // -------------------------
    // Device lock helper
    // -------------------------
    private fun lockDevice() {
        try {
            if (compName == null || dpm == null) {
                Log.w(TAG, "Cannot lock: compName or dpm is null")
                return
            }
            if (dpm!!.isAdminActive(compName!!)) {
                dpm!!.lockNow()
                Log.i(TAG, "Device locked via DevicePolicyManager")
            } else {
                Log.w(TAG, "Device admin not active; cannot lock")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Exception locking device", ex)
        }
    }

    // -------------------------
    // Notification helpers
    // -------------------------
    private fun buildNotification(isProblem: Boolean, problemText: String? = null): Notification {
        createChannelIfNeeded()
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val title = if (isProblem) "ARIA Secure Mode — Attention" else "ARIA Secure Mode Active"
        val content = problemText ?: if (isProblem) "Action required" else "Protecting this device"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val existing = nm?.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Foreground channel for ARIA Lost Mode"
                }
                nm?.createNotificationChannel(channel)
            }
        }
    }

    private fun updateNotificationProblem(message: String) {
        try {
            val n = buildNotification(isProblem = true, problemText = message)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIF_ID, n)
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to update problem notification", ex)
        }
    }

    private fun updateNotificationNormal() {
        try {
            val n = buildNotification(isProblem = false)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIF_ID, n)
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to update normal notification", ex)
        }
    }

    // -------------------------
    // Lifecycle
    // -------------------------
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        Log.i(TAG, "Service destroyed")
    }

    // -------------------------
    // Helpers
    // -------------------------
    private fun requiresLostMode(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val mode = prefs.getString("user_mode", "AI")
        val required = mode == "SECURE" || mode == "BOTH"
        Log.i(TAG, "requiresLostMode check: user_mode=$mode -> $required")
        return required
    }
}