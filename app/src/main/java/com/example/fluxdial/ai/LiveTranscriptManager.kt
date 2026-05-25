package com.example.fluxdial.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LiveTranscriptManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript
    private var isListening = false

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        isListening = true
        createAndStartRecognizer()
    }

    private fun createAndStartRecognizer() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                // Restore volume once listening starts
                audioManager.setStreamMute(android.media.AudioManager.STREAM_NOTIFICATION, false)
                audioManager.setStreamMute(android.media.AudioManager.STREAM_SYSTEM, false)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Restore volume on error
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
                
                if (isListening) {
                    android.os.Handler(
                        android.os.Looper.getMainLooper()
                    ).postDelayed({
                        if (isListening) createAndStartRecognizer()
                    }, 1000) // Increase delay to avoid rapid beeping on continuous error
                }
            }
            override fun onResults(results: Bundle?) {
                // Restore volume
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)

                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    _transcript.value = _transcript.value + " " + text
                }
                // Restart immediately for continuous listening
                if (isListening) createAndStartRecognizer()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                // Show partial results in real time
                val partial = partialResults
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )?.firstOrNull() ?: ""
                // Don't append partial — just observe for UI
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Keep listening without timeout
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }
        
        // Mute before starting to avoid beep
        audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_MUTE, 0)
        audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
        
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        
        // Ensure unmuted
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
        audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
    }

    fun getFullTranscript(): String = _transcript.value
    fun clearTranscript() { _transcript.value = "" }
}
