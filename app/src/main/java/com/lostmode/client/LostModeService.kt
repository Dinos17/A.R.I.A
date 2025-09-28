package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * ModeSelectionActivity
 *
 * Allows the user to select the operation mode of ARIA.
 * Two buttons: Secure Device Mode and Chat with AI (future feature).
 */
class ModeSelectionActivity : AppCompatActivity() {

    private lateinit var btnSecureDevice: Button
    private lateinit var btnChatAI: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)

        btnSecureDevice = findViewById(R.id.btnSecureDevice)
        btnChatAI = findViewById(R.id.btnChatAI)

        btnSecureDevice.setOnClickListener { selectSecureMode() }
        btnChatAI.setOnClickListener { launchChatAI() }
    }

    private fun selectSecureMode() {
        try {
            val prefs = getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
            prefs.edit().putString("user_mode", "SECURE").apply()

            Log.i("ModeSelectionActivity", "Secure mode selected. Navigating to MainActivity.")

            // Navigate to MainActivity with back stack cleared
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()

        } catch (ex: Exception) {
            Log.e("ModeSelectionActivity", "Error selecting secure mode", ex)
            Toast.makeText(
                this,
                "Failed to select secure mode: ${ex.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun launchChatAI() {
        Toast.makeText(this, "Chat with AI feature coming soon!", Toast.LENGTH_SHORT).show()
        Log.i("ModeSelectionActivity", "Chat with AI clicked — feature pending implementation.")
        // Future: startActivity(Intent(this, ChatAIActivity::class.java))
    }
}