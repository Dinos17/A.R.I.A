package com.lostmode.client

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * MainActivity
 *
 * Entry point after login and mode selection.
 * Handles permission requests, Device Admin setup, and starts LostModeService.
 */
class MainActivity : AppCompatActivity() {

    // Base permissions required for location
    private val basePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // Extra permission for Android 14+ (foreground service location)
    private val extraPermissions = if (Build.VERSION.SDK_INT >= 34) {
        arrayOf(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
    } else {
        emptyArray()
    }

    private val allPermissions = basePermissions + extraPermissions

    // Permission request launcher
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.entries.firstOrNull { !it.value }?.key
            if (denied != null) {
                Toast.makeText(this, "Permissions required: $denied", Toast.LENGTH_LONG).show()
            } else {
                proceedAfterPermissions()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Check if user is logged in
        if (!isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 2. Navigate to Mode Selection if mode not set
        if (!isModeSelected()) {
            startActivity(Intent(this, ModeSelectionActivity::class.java))
            finish()
            return
        }

        // 3. If Secure Device / Both modes, request permissions
        if (requiresSecurePermissions()) {
            if (!hasAllPermissions()) {
                requestPermissionsLauncher.launch(allPermissions)
            } else {
                proceedAfterPermissions()
            }
        } else {
            // AI Chat only → skip permissions and go to Devices screen
            navigateToDevicesScreen()
        }
    }

    /**
     * Called after all permissions are granted.
     * Checks Device Admin and starts LostModeService.
     */
    private fun proceedAfterPermissions() {
        // Device Admin check
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)

        if (!dpm.isAdminActive(compName)) {
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Required to enable Lost Mode features"
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Device Admin activation failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Start LostModeService
        val svcIntent = Intent(this, LostModeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, svcIntent)
        } else {
            startService(svcIntent)
        }

        navigateToDevicesScreen()
    }

    /**
     * Navigate to Device List screen
     */
    private fun navigateToDevicesScreen() {
        val devicesIntent = Intent(this, DeviceListActivity::class.java)
        startActivity(devicesIntent)
        finish()
    }

    /**
     * Check all required permissions are granted
     */
    private fun hasAllPermissions(): Boolean {
        basePermissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) return false
        }
        if (Build.VERSION.SDK_INT >= 34) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) return false
        }
        return true
    }

    // --- Helpers for login/mode state ---

    /**
     * Checks if user is logged in (token exists)
     */
    private fun isLoggedIn(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        return prefs.contains("auth_token") // fixed key
    }

    /**
     * Checks if user has selected a mode
     */
    private fun isModeSelected(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        return prefs.contains("user_mode")
    }

    /**
     * Checks if selected mode requires secure permissions
     */
    private fun requiresSecurePermissions(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val mode = prefs.getString("user_mode", "AI")
        return mode == "SECURE" || mode == "BOTH"
    }
}
