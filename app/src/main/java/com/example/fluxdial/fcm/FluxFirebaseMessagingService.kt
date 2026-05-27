package com.example.fluxdial.fcm

import android.content.Intent
import com.example.fluxdial.IncomingCallActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FluxFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid)
            .update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"]

        if (type == "incoming_call") {
            val callerUsername = data["callerUsername"] ?: "Unknown"
            val callId = data["callId"] ?: ""
            val callerUid = data["callerUid"] ?: ""

            val intent = Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("callerUsername", callerUsername)
                putExtra("callId", callId)
                putExtra("callerUid", callerUid)
                putExtra("isVoip", true)
            }
            startActivity(intent)
        }
    }
}
