package com.lostmode.client

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val basePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val extraPermissions = if (Build.VERSION.SDK_INT >= 34) {
        arrayOf(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
    } else emptyArray()

    private val allPermissions = basePermissions + extraPermissions

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

        try {
            if (!isLoggedIn()) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }

            if (!isModeSelected()) {
                startActivity(Intent(this, ModeSelectionActivity::class.java))
                finish()
                return
            }

            if (requiresSecurePermissions()) {
                if (!hasAllPermissions()) {
                    requestPermissionsLauncher.launch(allPermissions)
                } else {
                    proceedAfterPermissions()
                }
            } else {
                showDeviceDashboard()
            }

        } catch (ex: Exception) {
            Log.e("MainActivity", "Error initializing MainActivity: ${ex.message}", ex)
            Toast.makeText(this, "App initialization error: ${ex.message}", Toast.LENGTH_LONG).show()
            showDeviceDashboard()
        }
    }

    private fun proceedAfterPermissions() {
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)

            if (!dpm.isAdminActive(compName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Required to enable Lost Mode features"
                    )
                }
                startActivityForResult(intent, 1001)
                return
            }

            startLostModeService()

        } catch (ex: Exception) {
            Log.e("MainActivity", "Error during Device Admin / LostMode setup: ${ex.message}", ex)
            Toast.makeText(this, "Lost Mode initialization failed", Toast.LENGTH_LONG).show()
            showDeviceDashboard()
        }
    }

    private fun startLostModeService() {
        val svcIntent = Intent(this, LostModeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, svcIntent)
        } else {
            startService(svcIntent)
        }

        Toast.makeText(this, "Secure Mode setup started", Toast.LENGTH_SHORT).show()

        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(this, "Secure Mode active", Toast.LENGTH_SHORT).show()
            showDeviceDashboard()
        }, 1500)
    }

    private fun showDeviceDashboard() {
        // TODO: Implement the device dashboard view
        // Display:
        // - Device name
        // - Device photo
        // - Live location
        // Navigation to DeviceListActivity only if multiple devices exist
        startActivity(Intent(this, DeviceListActivity::class.java))
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode == Activity.RESULT_OK) {
                startLostModeService()
            } else {
                Toast.makeText(this, "Device Admin permission required for Secure Mode", Toast.LENGTH_LONG).show()
                showDeviceDashboard()
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        basePermissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        }
        if (Build.VERSION.SDK_INT >= 34) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    private fun isLoggedIn(): Boolean {
        return getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE).contains("auth_token")
    }

    private fun isModeSelected(): Boolean {
        return getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE).contains("user_mode")
    }

    private fun requiresSecurePermissions(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        val mode = prefs.getString("user_mode", "AI")
        return mode == "SECURE" || mode == "BOTH"
    }
}