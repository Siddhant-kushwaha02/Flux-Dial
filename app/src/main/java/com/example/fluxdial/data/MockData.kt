package com.example.fluxdial.data

import androidx.compose.runtime.mutableStateListOf
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
    val summary: String = "",
    val simLabel: String? = null
)

data class ConversationMemory(
    val id: String,
    val title: String,
    val participants: List<Contact>,
    val duration: String,
    val timestamp: String,
    val aiSummary: String,
    val actionItems: List<String> = emptyList(),
    val hasRecording: Boolean
)

object MockData {
    val callLogs = emptyList<CallLog>()
    val conversationMemories = mutableStateListOf<ConversationMemory>()
}
