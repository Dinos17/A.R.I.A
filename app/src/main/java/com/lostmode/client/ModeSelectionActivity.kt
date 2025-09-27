package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * ModeSelectionActivity
 *
 * Allows the user to select the operation mode of ARIA.
 * Currently only Secure Device Mode is available.
 * Stores the selected mode in SharedPreferences for MainActivity to read.
 */
class ModeSelectionActivity : AppCompatActivity() {

    private lateinit var btnSecureDevice: Button
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)

        btnSecureDevice = findViewById(R.id.btnSecureDevice)
        btnLogout = findViewById(R.id.btnLogout)

        btnSecureDevice.setOnClickListener { selectSecureMode() }
        btnLogout.setOnClickListener { logoutUser() }
    }

    private fun selectSecureMode() {
        try {
            getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
                .edit()
                .putString("user_mode", "SECURE")
                .apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (ex: Exception) {
            Toast.makeText(this, "Failed to select mode: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun logoutUser() {
        try {
            getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
                .edit()
                .remove("auth_token")
                .remove("user_mode")
                .apply()

            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } catch (ex: Exception) {
            Toast.makeText(this, "Failed to logout: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }
}