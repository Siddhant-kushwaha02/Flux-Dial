package com.example.fluxdial.telecom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.InCallService
import com.example.fluxdial.IncomingCallActivity
import com.example.fluxdial.MainActivity

/**
 * Service that interacts with the Android Telecom Framework.
 * Registered in AndroidManifest.xml as the InCallService.
 */
class FluxInCallService : InCallService() {

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            CallManager.updateCallState(state)
            if (state == Call.STATE_ACTIVE) {
                CallManager.markAsAnswered()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        call.registerCallback(callCallback)
        CallManager.onCallAdded(call)
        
        // Handle both incoming AND outgoing
        when (call.state) {
            Call.STATE_RINGING -> {
                CallManager.markAsIncoming()

                // STEP 1: Get raw number
                val callerNumber: String = try {
                    call.details?.handle?.schemeSpecificPart
                        ?.takeIf { it.isNotBlank() } ?: ""
                } catch (e: Exception) { "" }

                // STEP 2: Try callerDisplayName first
                val displayName: String? = try {
                    call.details?.callerDisplayName
                        ?.takeIf { it.isNotBlank() }
                } catch (e: Exception) { null }

                // STEP 3: If no displayName, look up from Contacts DB
                val contactName: String? = if (displayName != null) {
                    displayName
                } else if (callerNumber.isNotBlank()) {
                    lookupContactName(applicationContext, callerNumber)
                } else {
                    null
                }

                // STEP 4: Final fallback
                val callerName: String = contactName
                    ?: callerNumber.takeIf { it.isNotBlank() }
                    ?: "Unknown"

                // STEP 5: Store and use
                CallManager.setResolvedCallerInfo(callerName, callerNumber)

                // Show heads-up notification FIRST
                CallNotificationManager.showIncomingCallNotification(
                    context = applicationContext,
                    callerName = callerName,
                    callerNumber = callerNumber
                )

                // Start ringing and vibration
                FluxRingtoneManager.startRinging(applicationContext)

                // Launch IncomingCallActivity ONLY if the device is locked
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                if (keyguardManager.isDeviceLocked) {
                    val intent = Intent(this, IncomingCallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("caller_name", callerName)
                        putExtra("caller_number", callerNumber)
                    }
                    startActivity(intent)
                }
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE -> {
                CallManager.markAsOutgoing()
                if (call.state == Call.STATE_ACTIVE) {
                    CallManager.markAsAnswered()
                }
                // Cancel notification if call is active
                if (call.state == Call.STATE_ACTIVE) {
                    CallNotificationManager.cancelCallNotification(applicationContext)
                }
                // Launch MainActivity for outgoing calls with navigation extra
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra("navigate_to", "livecall")
                }
                startActivity(intent)
            }
            else -> {
                // Other states don't necessarily trigger UI launch
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        FluxRingtoneManager.stopRinging()
        
        // If call was never answered, show missed call notification
        if (CallManager.shouldShowMissedNotification()) {
            val callerName = CallManager.getCurrentCallerName() ?: "Unknown"
            val callerNumber = CallManager.getCurrentCallerNumber() ?: ""
            CallNotificationManager.showMissedCallNotification(
                applicationContext, callerName, callerNumber
            )
        }
        CallNotificationManager.cancelCallNotification(applicationContext)

        CallManager.resetCallFlags()
        call.unregisterCallback(callCallback)
        CallManager.onCallRemoved(call)
    }

    private fun lookupContactName(
        context: Context,
        phoneNumber: String
    ): String? {
        if (phoneNumber.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(
                        ContactsContract.PhoneLookup.DISPLAY_NAME
                    )
                    if (col >= 0) cursor.getString(col) else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
