package com.lostmode.client

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer

class MainActivity : Activity() {

    private lateinit var lostModeStatus: TextView
    private lateinit var gpsStatus: TextView
    private lateinit var connStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val REQUEST_PERMISSIONS = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lostModeStatus = findViewById(R.id.lostModeStatus)
        gpsStatus = findViewById(R.id.gpsStatus)
        connStatus = findViewById(R.id.connStatus)
        btnStart = findViewById(R.id.btnStartService)
        btnStop = findViewById(R.id.btnStopService)

        btnStart.setOnClickListener {
            startLostService()
        }
        btnStop.setOnClickListener {
            stopService(Intent(this, LostModeService::class.java))
            lostModeStatus.text = "Lost Mode: stopped"
            connStatus.text = "Connection: stopped"
        }

        LostModeService.statusLiveData.observe(this, Observer { status ->
            lostModeStatus.text = "Lost Mode: ${if (status.isActive) "Active" else "Inactive"}"
            gpsStatus.text = "GPS: ${status.lastCoordinates}"
            connStatus.text = "Connection: ${status.connectionStatus}"
        })

        ensureDeviceAdmin()
        checkAndRequestPermissions()
    }

    private fun ensureDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_enable_text))
            startActivity(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.SEND_SMS)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            startLostService()
        }
    }

    private fun startLostService() {
        val intent = Intent(this, LostModeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // Optional: handle onRequestPermissionsResult if you want to react immediately
}