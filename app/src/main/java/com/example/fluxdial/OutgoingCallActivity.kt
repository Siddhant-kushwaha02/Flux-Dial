package com.example.fluxdial

import android.content.Intent
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fluxdial.data.repository.CallRepository
import com.example.fluxdial.databinding.ActivityOutgoingCallBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OutgoingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOutgoingCallBinding
    private val repository = CallRepository()
    private val db = Firebase.firestore
    private var timerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOutgoingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callId = intent.getStringExtra("callId") ?: ""
        val calleeUsername = intent.getStringExtra("calleeUsername") ?: "Flux User"

        binding.tvTargetUsername.text = "@$calleeUsername"

        startPulseAnimation()
        listenToCallStatus(callId)

        binding.fabEndCall.setOnClickListener {
            lifecycleScope.launch {
                repository.updateCallStatus(callId, "ended")
                finish()
            }
        }

        // Auto-cancel after 30 seconds
        timerJob = lifecycleScope.launch {
            delay(30000)
            repository.updateCallStatus(callId, "ended")
            Toast.makeText(this@OutgoingCallActivity, "No answer", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startPulseAnimation() {
        val anim = ScaleAnimation(
            1f, 1.5f, 1f, 1.5f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        binding.pulseCircle.startAnimation(anim)
    }

    private fun listenToCallStatus(callId: String) {
        db.collection("calls").document(callId)
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getString("status")
                when (status) {
                    "accepted" -> {
                        timerJob?.cancel()
                        val intent = Intent(this, CallActivity::class.java).apply {
                            putExtra("callId", callId)
                            putExtra("isCaller", true)
                        }
                        startActivity(intent)
                        finish()
                    }
                    "declined" -> {
                        Toast.makeText(this, "Call declined", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    "ended" -> {
                        finish()
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
    }
}
