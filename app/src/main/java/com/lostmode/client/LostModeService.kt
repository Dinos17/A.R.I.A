package com.lostmode.client

import android.Manifest
import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * LostModeService
 *
 * Foreground service which:
 * - requests location updates (when permissions present)
 * - sends locations to the server
 * - handles server commands (lock, sound)
 *
 * Notes:
 * - This service will stop itself if required permissions are missing or if the user
 *   preference doesn't require lost mode.
 * - Tapping the notification opens MainActivity where the user can fix permissions/admin.
 */
class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val CHANNEL_ID = "lost_mode_channel"
        private const val CHANNEL_NAME = "ARIA System Services"
        private const val NOTIF_ID = 101

        private const val INTERVAL_MS = 10_000L
        private const val FASTEST_MS = 5_000L

        // Intent actions
        const val ACTION_START = "com.lostmode.client.action.START"
        const val ACTION_STOP = "com.lostmode.client.action.STOP"
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private var dpm: DevicePolicyManager? = null
    private var compName: ComponentName? = null
    private var lostModeActive = false
    private var ringtone: Ringtone? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location = result.lastLocation ?: return
            Log.d(TAG, "Location received: ${loc.latitude}, ${loc.longitude}")

            // Send location to the server on IO dispatcher
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val res = NetworkClient.sendLocationToServer(loc)
                    res.onSuccess { json ->
                        handleServerCommands(json)
                    }.onFailure { ex ->
                        Log.e(TAG, "Error sending location: ${ex.message}", ex)
                        updateNotificationProblem("Network error sending location")
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Exception in location coroutine", ex)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)

        // Prepare ringtone object now (may be null on some devices)
        val defaultUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        defaultUri?.let {
            ringtone = RingtoneManager.getRingtone(applicationContext, it)
        }

        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop action received")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                // Start foreground ASAP (must be called quickly after service start)
                startForeground(NOTIF_ID, buildNotificationNormal("Starting ARIA Lost Mode"))
                // If lost mode is not required, stop
                if (!requiresLostMode()) {
                    Log.i(TAG, "Lost Mode not required -> stopping service")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Check device admin. If active, set flag and lock immediately if requested by prefs
                if (compName != null && dpm?.isAdminActive(compName!!) == true) {
                    lostModeActive = true
                } else {
                    // Notify user via notification to open app and enable admin
                    updateNotificationProblem("Device admin inactive — open app to enable")
                }

                // Start location updates if we have permissions, otherwise notify user
                if (hasAllRequiredLocationPermissions()) {
                    startLocationUpdates()
                } else {
                    updateNotificationProblem("Missing location permissions — open app to grant")
                }
            }
            else -> {
                Log.w(TAG, "Unknown action: $action")
            }
        }

        // Keep service running if system kills it
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasAllRequiredLocationPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        val backgroundOk = if (Build.VERSION.SDK_INT >= 29)
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        else true

        val fgServiceLocOk = if (Build.VERSION.SDK_INT >= 34)
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        else true

        return fine && coarse && backgroundOk && fgServiceLocOk
    }

    private fun startLocationUpdates() {
        if (!hasAllRequiredLocationPermissions()) {
            Log.w(TAG, "startLocationUpdates: missing permissions")
            updateNotificationProblem("Missing location permissions — open app to grant")
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
            updateNotificationNormal("Lost Mode active — sending location")
            Log.i(TAG, "Location updates requested")
        } catch (ex: SecurityException) {
            Log.e(TAG, "Missing location permissions", ex)
            updateNotificationProblem("Missing location permissions — open app to grant")
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to request location updates", ex)
            updateNotificationProblem("Failed to start location updates")
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
            Log.i(TAG, "Location updates removed")
        } catch (ex: Exception) {
            Log.w(TAG, "Error removing location updates", ex)
        }
    }

    /**
     * Handles top-level server command flags.
     * Example expected JSON:
     * { "lock": true, "sound": true }
     */
    private fun handleServerCommands(json: JSONObject) {
        try {
            if (json.optBoolean("lock", false)) {
                Log.i(TAG, "Server requested lock")
                if (compName != null && dpm?.isAdminActive(compName!!) == true) {
                    lockDevice()
                    lostModeActive = true
                    updateNotificationNormal("Device locked by server")
                } else {
                    Log.w(TAG, "Admin inactive; cannot lock")
                    updateNotificationProblem("Server requested lock but admin inactive — open app")
                }
            }

            if (json.optBoolean("sound", false)) {
                Log.i(TAG, "Server requested sound")
                playAlertSound()
                updateNotificationNormal("Alert: playing sound")
            }

            // Future: parse queued commands or command objects
            // e.g. json.optJSONObject("command") ...
        } catch (ex: Exception) {
            Log.e(TAG, "Error handling server commands", ex)
        }
    }

    private fun lockDevice() {
        try {
            if (compName != null && dpm?.isAdminActive(compName!!) == true) {
                dpm?.lockNow()
                Log.i(TAG, "Device locked")
            } else {
                Log.w(TAG, "Cannot lock: admin inactive")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to lock device", ex)
        }
    }

    private fun playAlertSound() {
        try {
            ringtone?.let {
                if (!it.isPlaying) {
                    it.play()
                    // stop after a short delay to avoid infinite ringing
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            // play for ~6 seconds
                            kotlinx.coroutines.delay(6000)
                            if (it.isPlaying) it.stop()
                        } catch (ignored: Exception) { /* ignore */ }
                    }
                }
            } ?: run {
                // fallback: vibrate or send a high-priority notification
                Log.w(TAG, "No ringtone available to play")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to play alert sound", ex)
        }
    }

    private fun buildNotification(isProblem: Boolean, problemText: String? = null): Notification {
        createChannelIfNeeded()
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val stopIntent = Intent(this, LostModeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val title = if (isProblem) "ARIA Secure Mode — Attention" else "ARIA Secure Mode Active"
        val content = problemText ?: if (isProblem) "Action required" else "Protecting this device"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Add an action to open the app and to stop the service (user control)
        builder.addAction(
            android.R.drawable.ic_menu_view,
            "Open app",
            openPending
        )
        builder.addAction(
            android.R.drawable.ic_delete,
            "Stop",
            stopPending
        )

        return builder.build()
    }

    private fun buildNotificationNormal(message: String? = null): Notification {
        return buildNotification(false, message)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
                channel.description = "Foreground channel for ARIA Lost Mode"
                nm?.createNotificationChannel(channel)
            }
        }
    }

    private fun updateNotificationProblem(message: String) {
        val n = buildNotification(true, message)
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, n)
    }

    private fun updateNotificationNormal(message: String? = null) {
        val n = buildNotification(false, message)
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, n)
    }

    override fun onDestroy() {
        stopLocationUpdates()
        ringtone?.stop()
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    private fun requiresLostMode(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val mode = prefs.getString("user_mode", "AI")
        return mode == "SECURE" || mode == "BOTH"
    }
}
