package com.example.fluxdial

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fluxdial.databinding.ActivityAuthBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            handleAuth(isLogin = true)
        }

        binding.btnRegister.setOnClickListener {
            handleAuth(isLogin = false)
        }
    }

    private fun handleAuth(isLogin: Boolean) {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.pbLoading.visibility = View.VISIBLE
            try {
                if (isLogin) {
                    auth.signInWithEmailAndPassword(email, password).await()
                } else {
                    auth.createUserWithEmailAndPassword(email, password).await()
                }
                saveTokenAndNavigate()
            } catch (e: Exception) {
                Toast.makeText(this@AuthActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.pbLoading.visibility = View.GONE
            }
        }
    }

    private suspend fun saveTokenAndNavigate() {
        val uid = auth.currentUser?.uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        
        try {
            db.collection("users").document(uid)
                .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
