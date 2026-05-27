package com.example.fluxdial.data.repository

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallRepository {

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val functions = Firebase.functions

    suspend fun getUidByUsername(username: String): String? {
        val sanitized = username.removePrefix("@").trim().lowercase()
        return try {
            val doc = db.collection("usernames").document(sanitized).get().await()
            doc.getString("uid")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getFcmTokenByUid(uid: String): String? {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            doc.getString("fcmToken")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun initiateCall(calleeUid: String, callerUsername: String): String? {
        val callId = UUID.randomUUID().toString()
        val data = hashMapOf(
            "calleeUid" to calleeUid,
            "callerUsername" to callerUsername,
            "callId" to callId
        )

        return try {
            functions.getHttpsCallable("initiateCall").call(data).await()
            callId
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateCallStatus(callId: String, status: String) {
        try {
            db.collection("calls").document(callId).update("status", status).await()
        } catch (e: Exception) {
            // Handle if doc doesn't exist etc.
        }
    }
    
    suspend fun getCurrentUserUsername(): String? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val doc = db.collection("users").document(uid).get().await()
            doc.getString("username")
        } catch (e: Exception) {
            null
        }
    }
}
