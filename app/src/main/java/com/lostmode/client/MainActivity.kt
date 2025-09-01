package com.lostmode.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST = 1234
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.INTERNET
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request runtime permissions if not granted
        requestNeededPermissions()

        // Launch device admin prompt to allow locking
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to enable Lost Mode features")
            startActivity(intent)
        }

        // Start foreground service (LostModeService)
        val svcIntent = Intent(this, LostModeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, svcIntent)
        } else {
            startService(svcIntent)
        }

        Toast.makeText(this, "ARIA service is starting", Toast.LENGTH_SHORT).show()
        // Optionally finish the activity if you want it to disappear immediately:
        // finish()
    }

    private fun requestNeededPermissions() {
        val missing = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST) {
            // simple feedback
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions are required for ARIA to function", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}