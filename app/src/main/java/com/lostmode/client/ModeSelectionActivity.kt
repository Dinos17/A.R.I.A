package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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

        // Secure Device Mode selection
        btnSecureDevice.setOnClickListener {
            // Save selected mode in SharedPreferences
            getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
                .edit()
                .putString("user_mode", "SECURE")
                .apply()

            // Navigate to MainActivity to proceed with permissions & LostMode setup
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Logout clears auth token and returns to LoginActivity
        btnLogout.setOnClickListener {
            getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
                .edit()
                .remove("auth_token")
                .apply()

            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
