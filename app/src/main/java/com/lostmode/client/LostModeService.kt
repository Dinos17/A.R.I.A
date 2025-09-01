package com.lostmode.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*

data class LostModeStatus(
    val isActive: Boolean = false,
    val lastCoordinates: String = "n/a",
    val connectionStatus: String = "unknown"
)

class LostModeService : Service() {

    companion object {
        val statusLiveData: MutableLiveData<LostModeStatus> = MutableLiveData(LostModeStatus(false, "n/a", "idle"))
        private const val CHANNEL_ID = "lost_mode_channel"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location? = result.lastLocation
            loc?.let {
                val coords = "${it.latitude}, ${it.longitude}"
                statusLiveData.postValue(LostModeStatus(true, coords, "connected"))
                NetworkClient.sendLocation(it.latitude, it.longitude)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(1, buildNotification())
        configureLocationRequest()
        startLocationUpdates()
        statusLiveData.postValue(LostModeStatus(true, "requesting...", "starting"))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, "Lost Mode Service", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Update")
            .setContentText("Service running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun configureLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 30_000
            fastestInterval = 15_000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            statusLiveData.postValue(LostModeStatus(true, "permission_denied", "no-gps"))
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}