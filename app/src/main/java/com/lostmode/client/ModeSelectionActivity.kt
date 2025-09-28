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
            getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
                .edit()
                .putString("user_mode", "SECURE")
                .apply()

            // Start MainActivity (Secure Device dashboard)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (ex: Exception) {
            Toast.makeText(this, "Failed to select mode: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchChatAI() {
        Toast.makeText(this, "Chat with AI feature coming soon!", Toast.LENGTH_SHORT).show()
        // Future: startActivity(Intent(this, ChatAIActivity::class.java))
    }
}