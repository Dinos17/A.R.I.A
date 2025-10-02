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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val basePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val extraPermissions = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.POST_NOTIFICATIONS
        )
        else -> emptyArray()
    }

    private val allPermissions = basePermissions + extraPermissions

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.entries.firstOrNull { !it.value }?.key
            if (denied != null) {
                Toast.makeText(this, "Permission required: $denied", Toast.LENGTH_LONG).show()
                resetModeAndReturn()
            } else {
                proceedAfterPermissions()
            }
        }

    private val deviceAdminLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startLostModeService()
            } else {
                Toast.makeText(
                    this,
                    "Device Admin permission required for Secure Mode",
                    Toast.LENGTH_LONG
                ).show()
                resetModeAndReturn()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            intent.getStringExtra("REQUESTED_MODE")?.let { requested ->
                if (requested == "SECURE" || requested == "BOTH") {
                    getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putString("user_mode", requested)
                        .apply()
                }
            }

            when {
                !isLoggedIn() -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                !isModeSelected() -> {
                    startActivity(Intent(this, ModeSelectionActivity::class.java))
                    finish()
                }
                requiresSecurePermissions() -> {
                    if (!hasAllPermissions()) requestPermissionsLauncher.launch(allPermissions)
                    else proceedAfterPermissions()
                }
                else -> showDeviceDashboard()
            }
        } catch (ex: Exception) {
            Toast.makeText(this, "App initialization error", Toast.LENGTH_LONG).show()
            resetModeAndReturn()
        }
    }

    private fun proceedAfterPermissions() {
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val compName = ComponentName(this, AriaDeviceAdminReceiver::class.java)

            if (dpm != null && !dpm.isAdminActive(compName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Required to enable Lost Mode features"
                    )
                }
                deviceAdminLauncher.launch(intent)
                return
            }

            startLostModeService()

        } catch (ex: Exception) {
            Toast.makeText(this, "Lost Mode initialization failed", Toast.LENGTH_LONG).show()
            resetModeAndReturn()
        }
    }

    private fun startLostModeService() {
        val svcIntent = Intent(this, LostModeService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, svcIntent)
            } else startService(svcIntent)

            Toast.makeText(this, "Secure Mode setup started", Toast.LENGTH_SHORT).show()
            showDeviceDashboard()
        } catch (ex: Exception) {
            Toast.makeText(this, "Unable to start Secure Mode", Toast.LENGTH_LONG).show()
            resetModeAndReturn()
        }
    }

    private fun showDeviceDashboard() {
        startActivity(Intent(this, DeviceListActivity::class.java))
        finish()
    }

    private fun hasAllPermissions(): Boolean {
        return allPermissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun isLoggedIn(): Boolean {
        return getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE).contains("auth_token")
    }

    private fun isModeSelected(): Boolean {
        return getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE).contains("user_mode")
    }

    private fun requiresSecurePermissions(): Boolean {
        val prefs = getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
        return when (prefs.getString("user_mode", "AI")) {
            "SECURE", "BOTH" -> true
            else -> false
        }
    }

    private fun resetModeAndReturn() {
        getSharedPreferences("ARIA_PREFS", Context.MODE_PRIVATE)
            .edit()
            .remove("user_mode")
            .apply()

        startActivity(Intent(this, ModeSelectionActivity::class.java))
        finish()
    }
}
