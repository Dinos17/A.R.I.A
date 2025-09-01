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
import com.google.android.gms.location.*

class LostModeService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        startLocationUpdates()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "lost_mode_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "Lost Mode", 
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("System Update")
            .setContentText("Service running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }

    private fun startLocationUpdates() {
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        val request = LocationRequest.create().apply {
            interval = 30_000
            fastestInterval = 15_000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
        fusedClient.requestLocationUpdates(
            request, 
            locationCallback, 
            Looper.getMainLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // FIXED: null-safety for Location
            val loc: Location? = result.lastLocation
            loc?.let { NetworkClient.sendLocationToServer(it) }
        }
    }
}
