package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var signupButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Bind UI elements (match IDs in activity_login.xml)
        emailInput = findViewById(R.id.inputEmail)
        passwordInput = findViewById(R.id.inputPassword)
        loginButton = findViewById(R.id.btnLogin)
        signupButton = findViewById(R.id.btnSignUp) // ✅ Fixed ID to match XML

        // Handle Login
        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            } else {
                performLogin(email, password)
            }
        }

        // Handle Signup
        signupButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            } else {
                performSignup(email, password)
            }
        }
    }

    private fun performLogin(email: String, password: String) {
        sendAuthRequest(Config.LOGIN_ENDPOINT, email, password, "Login")
    }

    private fun performSignup(email: String, password: String) {
        sendAuthRequest(Config.SIGNUP_ENDPOINT, email, password, "Signup") // ✅ Now points to Config.SIGNUP_ENDPOINT
    }

    private fun sendAuthRequest(endpoint: String, email: String, password: String, action: String) {
        val payload = JSONObject().apply {
            put("email", email)
            put("password", password)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("AuthActivity", "$action failed: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string()
                    val json = responseBody?.let { str -> JSONObject(str) }

                    if (it.isSuccessful && json?.has("token") == true) {
                        val token = json.optString("token")

                        // Save token locally
                        getSharedPreferences("aria_prefs", MODE_PRIVATE)
                            .edit()
                            .putString("auth_token", token)
                            .apply()

                        runOnUiThread {
                            Toast.makeText(this@LoginActivity, "$action successful!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@LoginActivity, ModeSelectionActivity::class.java))
                            finish()
                        }
                    } else {
                        runOnUiThread {
                            val message = json?.optString("message", "$action failed")
                            Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }
}