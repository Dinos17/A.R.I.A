package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Entry point of the app.
 * Redirects user to LoginActivity if not authenticated,
 * otherwise to ModeSelectionActivity.
 */
class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("aria_prefs", MODE_PRIVATE)
        val token = prefs.getString("auth_token", null)

        if (token.isNullOrEmpty()) {
            // No token → Go to login
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            // Token exists → Go to mode selection
            startActivity(Intent(this, ModeSelectionActivity::class.java))
        }

        finish() // prevent user from returning here
    }
}
