package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * LauncherActivity
 *
 * Entry point of the app. Checks if user is authenticated.
 * Redirects to LoginActivity if no token, otherwise to ModeSelectionActivity.
 */
class LauncherActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LauncherActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val prefs = getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
            val token = prefs.getString("auth_token", null)

            if (token.isNullOrEmpty()) {
                Log.i(TAG, "No auth token found → navigating to LoginActivity")
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                Log.i(TAG, "Auth token found → navigating to ModeSelectionActivity")
                startActivity(Intent(this, ModeSelectionActivity::class.java))
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error checking auth token: ${ex.message}", ex)
            // Fallback: always navigate to login
            startActivity(Intent(this, LoginActivity::class.java))
        } finally {
            finish() // prevent user from returning here
        }
    }
}