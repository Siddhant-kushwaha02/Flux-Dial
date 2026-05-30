package com.example.fluxdial.ui.dial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxdial.data.repository.CallRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class DialViewModel : ViewModel() {

    private val repository = CallRepository()
    private val client = OkHttpClient()

    private val _callInitiated = MutableStateFlow<Pair<String, String>?>(null) // callId to calleeUsername
    val callInitiated: StateFlow<Pair<String, String>?> = _callInitiated

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun makeCall(targetUsername: String) {
        val callerUid = Firebase.auth.currentUser?.uid ?: return
        val callId = UUID.randomUUID().toString()
        val sanitizedTarget = targetUsername.removePrefix("@").trim().lowercase()

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 1. Lookup callee UID
                val calleeUid = repository.getUidByUsername(sanitizedTarget)
                if (calleeUid == null) {
                    _error.value = "User @$sanitizedTarget not found"
                    _isLoading.value = false
                    return@launch
                }

                // 2. Get my username
                val myUsername = repository.getCurrentUserUsername() ?: "Flux User"

                // 3. Notify backend server
                val success = withContext(Dispatchers.IO) {
                    val json = JSONObject().apply {
                        put("callerUid", callerUid)
                        put("calleeUid", calleeUid)
                        put("callerUsername", myUsername)
                        put("callId", callId)
                    }
                    val body = json.toString().toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("https://fluxdial-server.onrender.com/initiateCall")
                        .post(body)
                        .build()

                    try {
                        client.newCall(request).execute().use { response ->
                            response.isSuccessful
                        }
                    } catch (e: Exception) {
                        false
                    }
                }

                if (success) {
                    _callInitiated.value = Pair(callId, sanitizedTarget)
                } else {
                    _error.value = "Server error. Try again later."
                }
            } catch (e: Exception) {
                _error.value = "Connection failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
