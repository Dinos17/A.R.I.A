package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = NetworkClient.login(email, password)
                if (result.isSuccess) {
                    Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()

                    // Save token and register device
                    AriaApp.instance.saveAuthToken(result.getOrNull()!!)
                    CoroutineScope(Dispatchers.IO).launch {
                        AriaApp.instance.registerDeviceIfNeeded()
                    }

                    startActivity(Intent(this@LoginActivity, ModeSelectionActivity::class.java))
                    finish()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Login failed"
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (ex: Exception) {
                Toast.makeText(this@LoginActivity, "Login error: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun attemptSignup() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = NetworkClient.signup(email, password)
                if (result.isSuccess) {
                    Toast.makeText(this@LoginActivity, "Signup successful!", Toast.LENGTH_SHORT).show()

                    // Save token and register device
                    AriaApp.instance.saveAuthToken(result.getOrNull()!!)
                    CoroutineScope(Dispatchers.IO).launch {
                        AriaApp.instance.registerDeviceIfNeeded()
                    }

                    startActivity(Intent(this@LoginActivity, ModeSelectionActivity::class.java))
                    finish()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Signup failed"
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (ex: Exception) {
                Toast.makeText(this@LoginActivity, "Signup error: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
