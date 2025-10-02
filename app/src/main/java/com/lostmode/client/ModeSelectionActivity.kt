package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ModeSelectionActivity
 *
 * Allows the user to select the operation mode of ARIA.
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
            // Save the selected mode in SharedPreferences
            getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
                .edit()
                .putString("user_mode", "SECURE")
                .apply()

            // Register the device in background if needed
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AriaApp.instance.registerDeviceIfNeeded()
                } catch (ex: Exception) {
                    Log.e("ModeSelectionActivity", "Device registration failed", ex)
                }
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("REQUESTED_MODE", "SECURE")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()

        } catch (ex: Exception) {
            Log.e("ModeSelectionActivity", "Error selecting secure mode", ex)
            Toast.makeText(this, "Failed to select mode: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchChatAI() {
        Toast.makeText(this, "Chat with AI feature coming soon!", Toast.LENGTH_SHORT).show()
        // Future: startActivity(Intent(this, ChatAIActivity::class.java))
    }
}
