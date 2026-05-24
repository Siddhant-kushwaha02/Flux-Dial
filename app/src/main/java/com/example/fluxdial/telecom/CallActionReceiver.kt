package com.example.fluxdial.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.fluxdial.MainActivity

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotificationManager.ACTION_ANSWER -> {
                // Stop ringing
                FluxRingtoneManager.stopRinging()
                // Answer the call
                CallManager.acceptCall()
                // Cancel the incoming notification
                CallNotificationManager.cancelCallNotification(context)
                // Open live call screen
                val activityIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("navigate_to", "livecall")
                }
                context.startActivity(activityIntent)
            }
            CallNotificationManager.ACTION_DECLINE -> {
                // Stop ringing
                FluxRingtoneManager.stopRinging()
                // Reject the call
                CallManager.rejectCall()
                // Cancel notification
                CallNotificationManager.cancelCallNotification(context)
                // Show missed call notification
                val callerName = CallManager.getCurrentCallerName() ?: "Unknown"
                val callerNumber = CallManager.getCurrentCallerNumber() ?: ""
                CallNotificationManager.showMissedCallNotification(
                    context, callerName, callerNumber
                )
            }
        }
    }
}
