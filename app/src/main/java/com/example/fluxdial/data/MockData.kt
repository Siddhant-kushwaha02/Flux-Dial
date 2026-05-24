package com.example.fluxdial.data

import com.example.fluxdial.Contact

enum class CallType {
    INCOMING, OUTGOING, MISSED
}

data class CallLog(
    val id: String,
    val contact: Contact,
    val type: CallType,
    val duration: String,
    val timestamp: String,
    val isPriority: Boolean = false,
    val summary: String = ""
)

val callLogs = emptyList<CallLog>()
