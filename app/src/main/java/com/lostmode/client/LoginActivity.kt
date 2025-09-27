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

        emailInput = findViewById(R.id.inputEmail)
        passwordInput = findViewById(R.id.inputPassword)
        loginButton = findViewById(R.id.btnLogin)
        signupButton = findViewById(R.id.btnSignUp)

        loginButton.setOnClickListener { attemptLogin() }
        signupButton.setOnClickListener { attemptSignup() }
    }

    private fun attemptLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            return
        }
        performAuth(Config.LOGIN_ENDPOINT, email, password, "Login")
    }

    private fun attemptSignup() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            return
        }
        performAuth(Config.SIGNUP_ENDPOINT, email, password, "Signup")
    }

    private fun performAuth(endpoint: String, email: String, password: String, action: String) {
        val payload = JSONObject().apply {
            put("email", email)
            put("password", password)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(endpoint).post(body).build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("LoginActivity", "$action failed: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val responseBody = it.body?.string()
                        val json = responseBody?.let { str -> JSONObject(str) }

                        if (it.isSuccessful && json?.has("token") == true) {
                            val token = json.optString("token")
                            getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
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
                } catch (ex: Exception) {
                    Log.e("LoginActivity", "Error parsing $action response: ${ex.message}", ex)
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Unexpected error occurred", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}