package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * Entry point of the app.
 * Redirects user to LoginActivity if not authenticated,
 * otherwise to ModeSelectionActivity.
 */
class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val prefs = getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
            val token = prefs.getString("auth_token", null)

            if (token.isNullOrEmpty()) {
                Log.i("LauncherActivity", "No auth token found → navigating to LoginActivity")
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                Log.i("LauncherActivity", "Auth token found → navigating to ModeSelectionActivity")
                startActivity(Intent(this, ModeSelectionActivity::class.java))
            }

        } catch (ex: Exception) {
            Log.e("LauncherActivity", "Error checking token: ${ex.message}", ex)
            // Fallback: always go to login
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish() // prevent user from returning here
    }
}