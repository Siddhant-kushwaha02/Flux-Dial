package com.example.fluxdial.telecom

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton to manage the active call state across the app.
 */
object CallManager {
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall

    private val _callState = MutableStateFlow(Call.STATE_NEW)
    val callState: StateFlow<Int> = _callState

    private var _wasIncomingCall = false
    private var _wasAnswered = false
    private var _resolvedCallerName: String? = null
    private var _resolvedCallerNumber: String? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _callState.value = state
            if (state == Call.STATE_ACTIVE) {
                _wasAnswered = true
            }
            if (state == Call.STATE_DISCONNECTED) {
                _currentCall.value = null
            }
        }
    }

    fun setCall(call: Call?) {
        _currentCall.value = call
        if (call != null) {
            _callState.value = call.state
            call.registerCallback(callCallback)
        } else {
            _callState.value = Call.STATE_DISCONNECTED
        }
    }

    fun onCallAdded(call: Call) {
        setCall(call)
    }

    fun onCallRemoved(call: Call) {
        if (_currentCall.value == call) {
            call.unregisterCallback(callCallback)
            _currentCall.value = null
            _callState.value = Call.STATE_DISCONNECTED
        }
    }

    fun updateCallState(state: Int) {
        _callState.value = state
    }

    fun getCallDuration(): String {
        val call = _currentCall.value ?: return "00:00"
        val details = call.details
        val connectTime = details?.connectTimeMillis ?: return "00:00"
        if (connectTime == 0L) return "00:00"
        val elapsed = (System.currentTimeMillis() - connectTime) / 1000
        val minutes = elapsed / 60
        val seconds = elapsed % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    fun endCall() {
        _currentCall.value?.disconnect()
        _currentCall.value = null
        _callState.value = Call.STATE_DISCONNECTED
    }

    fun disconnectCall() {
        endCall()
    }

    fun answerCall() {
        _currentCall.value?.answer(0)
    }

    fun acceptCall() {
        answerCall()
    }

    fun rejectCall() {
        _currentCall.value?.reject(false, null)
    }

    fun isCallActive(): Boolean {
        return _callState.value == Call.STATE_ACTIVE
    }

    fun isOutgoing(): Boolean {
        return _callState.value == Call.STATE_DIALING || 
               _callState.value == Call.STATE_CONNECTING
    }

    fun setResolvedCallerInfo(name: String, number: String) {
        _resolvedCallerName = name
        _resolvedCallerNumber = number
    }

    fun getCurrentCallerName(): String? = _resolvedCallerName
        ?: _currentCall.value?.details?.callerDisplayName?.toString()
        ?: _currentCall.value?.details?.handle?.schemeSpecificPart

    fun getCurrentCallerNumber(): String? = _resolvedCallerNumber
        ?: _currentCall.value?.details?.handle?.schemeSpecificPart

    fun markAsIncoming() { _wasIncomingCall = true; _wasAnswered = false }
    fun markAsAnswered() { _wasAnswered = true }
    fun markAsOutgoing() { _wasIncomingCall = false; _wasAnswered = false }
    fun shouldShowMissedNotification(): Boolean {
        return _wasIncomingCall && !_wasAnswered
    }
    fun resetCallFlags() {
        _wasIncomingCall = false
        _wasAnswered = false
        _resolvedCallerName = null
        _resolvedCallerNumber = null
    }

    // AI Placeholders
    fun performAiSpamDetection(number: String): String {
        return "Safe Call" 
    }

    fun performAiAnswer() {
        answerCall()
    }
}
