package com.example.fluxdial.telecom

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fluxdial.IncomingCallActivity
import com.example.fluxdial.MainActivity
import com.example.fluxdial.R

object CallNotificationManager {

    const val CHANNEL_ID = "flux_incoming_call"
    const val NOTIFICATION_ID = 1001
    const val ACTION_ANSWER = "com.example.fluxdial.ACTION_ANSWER"
    const val ACTION_DECLINE = "com.example.fluxdial.ACTION_DECLINE"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(false) // We handle vibration manually
                setSound(null, null)   // We handle sound manually
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Required for Android 12+ lock screen calls:
                setBypassDnd(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        callerName: String,
        callerNumber: String,
        isVideo: Boolean = false,
        callId: String? = null,
        isVoip: Boolean = false
    ) {
        createNotificationChannel(context)
        
        val contentText = if (isVoip) "VoIP Call from $callerName" else if (isVideo) "Incoming video call: $callerNumber" else "Incoming call: $callerNumber"

        // Full screen intent — opens IncomingCallActivity
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("caller_name", callerName)
            putExtra("caller_number", callerNumber)
            putExtra("is_video", isVideo)
            putExtra("callerUsername", callerName)
            putExtra("callId", callId)
            putExtra("isVoip", isVoip)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Answer action
        val answerIntent = Intent(ACTION_ANSWER).apply {
            setPackage(context.packageName)
            putExtra("number", callerNumber)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context, 1, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline action
        val declineIntent = Intent(ACTION_DECLINE).apply {
            setPackage(context.packageName)
            putExtra("number", callerNumber)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context, 2, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Using existing icon
            .setContentTitle(callerName)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(if (isVideo) Color.GREEN else 0xFF0164B4.toInt())
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            // Answer button
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_launcher_foreground,
                    "Pick Up",
                    answerPendingIntent
                ).build()
            )
            // Decline button
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_launcher_foreground,
                    "Hang Up",
                    declinePendingIntent
                ).build()
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showMissedCallNotification(
        context: Context,
        callerName: String,
        callerNumber: String
    ) {
        cancelCallNotification(context)

        val callBackIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("dial_number", callerNumber)
        }
        val callBackPendingIntent = PendingIntent.getActivity(
            context, 3, callBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Missed call")
            .setContentText("$callerName ($callerNumber)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(callBackPendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_launcher_foreground,
                    "Call Back",
                    callBackPendingIntent
                ).build()
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelCallNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
