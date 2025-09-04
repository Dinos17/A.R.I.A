​package com.lostmode.client

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

/**

ARIA Lost Mode Foreground Service

Fully compatible with Android 14+ (API 34)

Handles location updates, device locking, and alert sounds.
*/
class LostModeService : Service() {

companion object {
private const val TAG = "LostModeService"
private const val CHANNEL_ID = "lost_mode_channel"
private const val CHANNEL_NAME = "System Services"
private const val NOTIF_ID = 101
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

// Verify Android 14 FOREGROUND_SERVICE_LOCATION permission  
     if (Build.VERSION.SDK_INT >= 34 &&  
         ContextCompat.checkSelfPermission(  
             this,  
             Manifest.permission.FOREGROUND_SERVICE_LOCATION  
         ) != PackageManager.PERMISSION_GRANTED  
     ) {  
         Log.e(TAG, "Missing FOREGROUND_SERVICE_LOCATION permission — location updates will not start")  
     }  

     startForeground(NOTIF_ID, buildNotification())  
     startLocationUpdates()  
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
     Log.i(TAG, "Location updates requested.")  
 } catch (secEx: SecurityException) {  
     Log.w(TAG, "SecurityException requesting location updates: ${secEx.message}")  
 } catch (ex: Exception) {  
     Log.e(TAG, "Exception requesting location updates: ${ex.message}", ex)  
 }

}

private val locationCallback = object : LocationCallback() {
override fun onLocationResult(result: LocationResult) {
try {
val loc: Location? = result.lastLocation
if (loc != null) {
Log.d(TAG, "Got location: ${loc.latitude}, ${loc.longitude} (acc=${loc.accuracy})")
NetworkClient.sendLocationToServer(loc) { json ->
try { handleServerCommands(json) } catch (ex: Exception) { Log.e(TAG, ex.message ?: "", ex) }
}
} else {
Log.w(TAG, "LocationResult has null lastLocation")
}
} catch (ex: Exception) {
Log.e(TAG, "onLocationResult exception: ${ex.message}", ex)
}
}
}

private fun handleServerCommands(json: JSONObject) {
try {
Log.d(TAG, "Server response: $json")
if (json.optBoolean("lock", false)) lockDevice()
if (json.optBoolean("play_sound", false)) playAlertSound()
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
lastRingtone?.takeIf { it.isPlaying }?.stop()
lastRingtone = null
} catch (ex: Exception) {
Log.e(TAG, "Failed to stop ringtone: ${ex.message}", ex)
}
}

override fun onDestroy() {
try {
fusedClient.removeLocationUpdates(locationCallback)
} catch (ex: Exception) {
Log.w(TAG, "Failed to remove location updates: ${ex.message}")
}
stopAlertSound()
Log.i(TAG, "Service destroyed")
super.onDestroy()
}
}


