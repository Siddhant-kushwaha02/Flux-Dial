package com.example.fluxdial.data.repository

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CallRepository {

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    suspend fun getUidByUsername(username: String): String? {
        val sanitized = username.removePrefix("@").trim().lowercase()
        return try {
            val doc = db.collection("usernames").document(sanitized).get().await()
            doc.getString("uid")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUsername(uid: String, username: String) {
        val sanitized = username.removePrefix("@").trim().lowercase()
        try {
            val batch = db.batch()
            batch.set(db.collection("usernames").document(sanitized), mapOf("uid" to uid))
            batch.set(db.collection("users").document(uid), mapOf("username" to sanitized), com.google.firebase.firestore.SetOptions.merge())
            batch.commit().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveFcmToken(uid: String, token: String) {
        try {
            db.collection("users").document(uid).update("fcmToken", token).await()
        } catch (e: Exception) {
            // Document might not exist
            db.collection("users").document(uid).set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    suspend fun updateCallStatus(callId: String, status: String) {
        try {
            db.collection("calls").document(callId).update("status", status).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun listenToCallStatus(callId: String): Flow<String?> = callbackFlow {
        val registration = db.collection("calls").document(callId)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getString("status"))
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveOffer(callId: String, sdp: String) {
        try {
            db.collection("calls").document(callId).update("offer", mapOf("sdp" to sdp, "type" to "offer")).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveAnswer(callId: String, sdp: String) {
        try {
            db.collection("calls").document(callId).update("answer", mapOf("sdp" to sdp, "type" to "answer")).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveIceCandidate(callId: String, uid: String, candidate: Map<String, Any>) {
        try {
            db.collection("calls").document(callId)
                .collection("iceCandidates").document(uid)
                .collection("candidates").add(candidate).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun listenForAnswer(callId: String): Flow<String?> = callbackFlow {
        val registration = db.collection("calls").document(callId)
            .addSnapshotListener { snapshot, _ ->
                val answer = snapshot?.get("answer") as? Map<*, *>
                trySend(answer?.get("sdp") as? String)
            }
        awaitClose { registration.remove() }
    }

    fun listenForOffer(callId: String): Flow<String?> = callbackFlow {
        val registration = db.collection("calls").document(callId)
            .addSnapshotListener { snapshot, _ ->
                val offer = snapshot?.get("offer") as? Map<*, *>
                trySend(offer?.get("sdp") as? String)
            }
        awaitClose { registration.remove() }
    }

    fun listenForIceCandidates(callId: String, remoteUid: String): Flow<Map<String, Any>> = callbackFlow {
        val registration = db.collection("calls").document(callId)
            .collection("iceCandidates").document(remoteUid)
            .collection("candidates")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        trySend(change.document.data)
                    }
                }
            }
        awaitClose { registration.remove() }
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
