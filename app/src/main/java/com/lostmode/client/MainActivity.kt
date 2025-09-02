package com.lostmode.client

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // We will request these permissions at runtime (add FOREGROUND_SERVICE_LOCATION on Android 14+)
    private val basePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.INTERNET
    )

    private val extraPermissions = if (Build.VERSION.SDK_INT >= 34) {
        arrayOf(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
    } else {
        emptyArray()
    }

    // Combined array for request
    private val allPermissions = basePermissions + extraPermissions

    // ActivityResult API to request permissions and get result
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            // Check whether required permission was granted
            val denied = result.entries.firstOrNull { !it.value }?.key
            if (denied != null) {
                Toast.makeText(this, "Permissions required: $denied", Toast.LENGTH_LONG).show()
                // Optionally, show a UI explaining why permissions are needed.
            } else {
                // All requested permissions granted -> proceed to enable device admin and start service
                proceedAfterPermission()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // If we already have necessary permissions, proceed; otherwise request them
        if (!hasAllPermissions()) {
            requestPermissionsLauncher.launch(allPermissions)
        } else {
            proceedAfterPermission()
        }
    }

    private fun proceedAfterPermission() {
        // Request device admin if not already active
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to enable Lost Mode features")
            }
            startActivity(intent)
        }

        // Start foreground service (only after permissions)
        val svcIntent = Intent(this, LostModeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, svcIntent)
        } else {
            startService(svcIntent)
        }

        Toast.makeText(this, "ARIA service starting...", Toast.LENGTH_SHORT).show()

        // Close activity to make app disappear (optional)
        finish()
    }

    private fun hasAllPermissions(): Boolean {
        // Confirm base permissions
        basePermissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) return false
        }
        // Confirm FOREGROUND_SERVICE_LOCATION on Android 14+
        if (Build.VERSION.SDK_INT >= 34) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }
}