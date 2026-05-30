package com.example.fluxdial

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fluxdial.data.repository.CallRepository
import com.example.fluxdial.databinding.ActivityIncomingCallBinding
import com.example.fluxdial.telecom.CallManager
import com.example.fluxdial.telecom.FluxRingtoneManager
import kotlinx.coroutines.launch

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private val repository = CallRepository()
    private var timerJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show over lockscreen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callerUsername = intent.getStringExtra("callerUsername") ?: "Unknown"
        val callId = intent.getStringExtra("callId") ?: ""
        val isVoip = intent.getBooleanExtra("isVoip", false)

        binding.tvCallerUsername.text = "@$callerUsername"

        binding.fabAccept.setOnClickListener {
            FluxRingtoneManager.stopRinging()
            if (isVoip && callId.isNotEmpty()) {
                lifecycleScope.launch {
                    repository.updateCallStatus(callId, "accepted")
                    // Navigate to CallActivity (to be built)
                    val intent = Intent(this@IncomingCallActivity, CallActivity::class.java).apply {
                        putExtra("callId", callId)
                        putExtra("isCaller", false)
                    }
                    startActivity(intent)
                    finish()
                }
            } else {
                CallManager.acceptCall()
                finish()
            }
        }

        binding.fabDecline.setOnClickListener {
            FluxRingtoneManager.stopRinging()
            if (isVoip && callId.isNotEmpty()) {
                lifecycleScope.launch {
                    repository.updateCallStatus(callId, "declined")
                    finish()
                }
            } else {
                CallManager.rejectCall()
                finish()
            }
        }
        
        if (isVoip) {
            FluxRingtoneManager.startRinging(this)

            // Auto-decline after 30 seconds
            timerJob = lifecycleScope.launch {
                kotlinx.coroutines.delay(30000)
                repository.updateCallStatus(callId, "declined")
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        FluxRingtoneManager.stopRinging()
    }
}
