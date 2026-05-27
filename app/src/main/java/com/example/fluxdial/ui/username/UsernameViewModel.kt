package com.example.fluxdial.ui.username

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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

    fun checkAvailability(username: String) {
        if (username.length < 3) {
            _isAvailable.value = null
            return
        }

        viewModelScope.launch {
            try {
                val doc = db.collection("usernames").document(username).get().await()
                _isAvailable.value = !doc.exists()
            } catch (e: Exception) {
                _isAvailable.value = null
            }
        }
    }

    fun saveUsername(username: String) {
        val uid = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Use a transaction or batch to ensure atomic write
                val batch = db.batch()
                
                // 1. Add to usernames collection for lookup/uniqueness
                val usernameRef = db.collection("usernames").document(username)
                batch.set(usernameRef, mapOf("uid" to uid))
                
                // 2. Update user document
                val userRef = db.collection("users").document(uid)
                batch.update(userRef, "username", username)
                
                batch.commit().await()
                _saveSuccess.value = true
            } catch (e: Exception) {
                // If update fails (e.g. document doesn't exist), try set
                try {
                    val batch = db.batch()
                    batch.set(db.collection("usernames").document(username), mapOf("uid" to uid))
                    batch.set(db.collection("users").document(uid), mapOf("username" to username), com.google.firebase.firestore.SetOptions.merge())
                    batch.commit().await()
                    _saveSuccess.value = true
                } catch (e2: Exception) {
                    _saveSuccess.value = false
                }
            } finally {
                _isSaving.value = false
            }
        }
    }
}
