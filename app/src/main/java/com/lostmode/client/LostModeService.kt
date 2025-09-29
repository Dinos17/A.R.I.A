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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val CHANNEL_ID = "lost_mode_channel"
        private const val CHANNEL_NAME = "ARIA System Services"
        private const val NOTIF_ID = 101

        private const val INTERVAL_MS = 10_000L
        private const val FASTEST_MS = 5_000L
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private var dpm: DevicePolicyManager? = null
    private var compName: ComponentName? = null
    private var lostModeActive = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location = result.lastLocation ?: return
            Log.d(TAG, "Location received: ${loc.latitude}, ${loc.longitude}")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val res = NetworkClient.sendLocationToServer(loc)
                    res.onSuccess { json ->
                        handleServerCommands(json)
                    }.onFailure { ex ->
                        Log.e(TAG, "Error sending location: ${ex.message}", ex)
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
        startForeground(NOTIF_ID, buildNotification(false))

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)

        if (!requiresLostMode()) {
            Log.i(TAG, "Lost Mode not required -> stopping service")
            stopSelf()
            return
        }

        if (compName != null && dpm?.isAdminActive(compName!!) == true) {
            lockDevice()
            lostModeActive = true
        } else {
            Log.w(TAG, "Device admin inactive")
            updateNotificationProblem("Device admin inactive — tap to enable.")
        }

        if (hasAllRequiredLocationPermissions()) startLocationUpdates()
        else updateNotificationProblem("Missing location permissions — tap to grant.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
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
        if (!hasAllRequiredLocationPermissions()) return

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
            updateNotificationNormal()
        } catch (ex: SecurityException) {
            Log.e(TAG, "Missing location permissions", ex)
            updateNotificationProblem("Missing location permissions — tap to grant.")
        }
    }

    private fun stopLocationUpdates() {
        try { fusedClient.removeLocationUpdates(locationCallback) }
        catch (ex: Exception) { Log.w(TAG, "Error removing location updates", ex) }
    }

    private fun handleServerCommands(json: JSONObject) {
        if (json.optBoolean("lock", false)) {
            Log.i(TAG, "Server requested lock")
            if (dpm?.isAdminActive(compName!!) == true) {
                lockDevice()
                lostModeActive = true
            } else {
                updateNotificationProblem("Server requested lock but admin inactive — tap to enable.")
            }
        }
    }

    private fun lockDevice() {
        if (compName != null && dpm?.isAdminActive(compName!!) == true) {
            dpm!!.lockNow()
            Log.i(TAG, "Device locked")
        } else Log.w(TAG, "Cannot lock: admin inactive")
    }

    private fun buildNotification(isProblem: Boolean, problemText: String? = null): Notification {
        createChannelIfNeeded()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
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

    private fun updateNotificationNormal() {
        val n = buildNotification(false)
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, n)
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    private fun requiresLostMode(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val mode = prefs.getString("user_mode", "AI")
        return mode == "SECURE" || mode == "BOTH"
    }
}