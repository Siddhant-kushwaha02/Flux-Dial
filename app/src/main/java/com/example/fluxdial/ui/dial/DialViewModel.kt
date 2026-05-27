package com.example.fluxdial.ui.dial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxdial.data.repository.CallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DialViewModel : ViewModel() {

    private val repository = CallRepository()

    private val _callInitiated = MutableStateFlow<String?>(null)
    val callInitiated: StateFlow<String?> = _callInitiated

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun makeCall(targetUsername: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            val targetUid = repository.getUidByUsername(targetUsername)
            if (targetUid == null) {
                _error.value = "User not found"
                _isLoading.value = false
                return@launch
            }

            val myUsername = repository.getCurrentUserUsername() ?: "Flux User"
            val callId = repository.initiateCall(targetUid, myUsername)
            
            if (callId != null) {
                _callInitiated.value = callId
            } else {
                _error.value = "Failed to initiate call"
            }
            _isLoading.value = false
        }
    }
}
