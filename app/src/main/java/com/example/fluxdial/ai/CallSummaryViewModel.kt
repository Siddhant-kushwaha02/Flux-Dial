package com.example.fluxdial.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxdial.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class CallSummaryViewModel(application: Application)
    : AndroidViewModel(application) {

    private val transcriptManager =
        LiveTranscriptManager(application.applicationContext)

    val transcript: StateFlow<String> = transcriptManager.transcript
    val liveText: StateFlow<String> = transcriptManager.liveText

    private val _summary = MutableStateFlow(
        CallSummaryResult("Listening...", emptyList(), emptyList())
    )
    val summary: StateFlow<CallSummaryResult> = _summary

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private var summaryJob: Job? = null

    fun startSession(clearTranscript: Boolean = true) {
        if (clearTranscript) {
            transcriptManager.clearTranscript()
        }
        transcriptManager.startListening()
        _isListening.value = true

        // Summarise every 45 seconds if content exists
        summaryJob = viewModelScope.launch {
            var lastTranscriptLength = 0
            while (isActive) {
                delay(45_000)
                val currentTranscript = transcriptManager.getFullTranscript()
                if (currentTranscript.isNotBlank() && currentTranscript.length > lastTranscriptLength + 20) {
                    val result = GeminiSummaryService.summarise(
                        currentTranscript,
                        BuildConfig.GEMINI_API_KEY
                    )
                    _summary.value = result
                    lastTranscriptLength = currentTranscript.length
                }
            }
        }
    }

    fun requestSummaryNow() {
        viewModelScope.launch {
            val currentTranscript = transcriptManager.getFullTranscript()
            if (currentTranscript.isNotBlank()) {
                _summary.value = GeminiSummaryService.summarise(
                    currentTranscript,
                    BuildConfig.GEMINI_API_KEY
                )
            }
        }
    }

    fun stopSession(): CallSummaryResult {
        summaryJob?.cancel()
        transcriptManager.stopListening()
        _isListening.value = false
        return _summary.value
    }

    override fun onCleared() {
        super.onCleared()
        transcriptManager.stopListening()
        summaryJob?.cancel()
    }
}
