package com.example.fluxdial.fcm

import com.example.fluxdial.telecom.CallNotificationManager
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

            CallNotificationManager.showIncomingCallNotification(
                context = this,
                callerName = callerUsername,
                callerNumber = callerUsername,
                isVideo = false,
                callId = callId,
                isVoip = true
            )
        }
    }
}
