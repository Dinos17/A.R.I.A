package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ModeSelectionActivity : AppCompatActivity() {

    private lateinit var btnSecureDevice: Button
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)

        btnSecureDevice = findViewById(R.id.btnSecureDevice)
        btnLogout = findViewById(R.id.btnLogout)

        // Only Secure Device Mode for now
        btnSecureDevice.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Logout clears token and goes back to LoginActivity
        btnLogout.setOnClickListener {
            getSharedPreferences("aria_prefs", MODE_PRIVATE)
                .edit()
                .remove("auth_token")
                .apply()

            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
