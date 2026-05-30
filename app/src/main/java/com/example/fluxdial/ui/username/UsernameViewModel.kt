package com.example.fluxdial.ui.username

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UsernameViewModel : ViewModel() {

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private val _isAvailable = MutableStateFlow<Boolean?>(null)
    val isAvailable: StateFlow<Boolean?> = _isAvailable

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private var checkJob: Job? = null

    fun checkAvailability(username: String) {
        val sanitized = username.removePrefix("@").trim().lowercase()
        if (sanitized.length < 3) {
            _isAvailable.value = null
            return
        }

        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            delay(500) // Debounce
            try {
                val doc = db.collection("usernames").document(sanitized).get().await()
                _isAvailable.value = !doc.exists()
            } catch (e: Exception) {
                _isAvailable.value = null
            }
        }
    }

    fun saveUsername(username: String) {
        val uid = auth.currentUser?.uid ?: return
        val sanitized = username.removePrefix("@").trim().lowercase()
        
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val batch = db.batch()
                
                // Uniqueness lookup
                val usernameRef = db.collection("usernames").document(sanitized)
                batch.set(usernameRef, mapOf("uid" to uid))
                
                // User profile
                val userRef = db.collection("users").document(uid)
                batch.set(userRef, mapOf("username" to sanitized), com.google.firebase.firestore.SetOptions.merge())
                
                batch.commit().await()
                _saveSuccess.value = true
            } catch (e: Exception) {
                _saveSuccess.value = false
            } finally {
                _isSaving.value = false
            }
        }
    }
}
