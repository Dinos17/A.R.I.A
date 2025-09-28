package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * LauncherActivity
 *
 * Entry point of the app.
 * Redirects user based on authentication and selected mode.
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
            val userMode = prefs.getString("user_mode", null)

            when {
                token.isNullOrEmpty() -> {
                    Log.i(TAG, "No auth token found → navigating to LoginActivity")
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                userMode.isNullOrEmpty() -> {
                    Log.i(TAG, "User logged in but mode not selected → navigating to ModeSelectionActivity")
                    startActivity(Intent(this, ModeSelectionActivity::class.java))
                }
                else -> {
                    Log.i(TAG, "User logged in with mode=$userMode → navigating to MainActivity")
                    val intent = Intent(this, MainActivity::class.java)
                    // Clear back stack to prevent going back to login/mode selection
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }

        } catch (ex: Exception) {
            Log.e(TAG, "Error during launch flow: ${ex.message}", ex)
            // Fallback: always navigate to LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        } finally {
            finish() // prevent returning to launcher
        }
    }
}