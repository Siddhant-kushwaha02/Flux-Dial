package com.example.fluxdial.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class LiveTranscriptManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript
    
    private val _liveText = MutableStateFlow("")
    val liveText: StateFlow<String> = _liveText

    private var isListening = false

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        isListening = true
        createAndStartRecognizer()
    }

    private fun createAndStartRecognizer() {
        if (!isListening) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Restore volume on error
                try {
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_DTMF, android.media.AudioManager.ADJUST_UNMUTE, 0)
                } catch (e: Exception) { e.printStackTrace() }
                
                android.util.Log.e("FluxDialAI", "Recognizer Error: $error")
                
                if (isListening) {
                    android.os.Handler(
                        android.os.Looper.getMainLooper()
                    ).postDelayed({
                        if (isListening) createAndStartRecognizer()
                    }, 2000)
                }
            }
            override fun onResults(results: Bundle?) {
                // Restore volume
                try {
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_DTMF, android.media.AudioManager.ADJUST_UNMUTE, 0)
                } catch (e: Exception) { e.printStackTrace() }

                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    _transcript.value = (_transcript.value + " " + text).trim()
                    _liveText.value = "" // Clear live text when result is finalized
                }
                
                if (isListening) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isListening) createAndStartRecognizer()
                    }, 1000)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    _liveText.value = text
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }
        
        try {
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_DTMF, android.media.AudioManager.ADJUST_MUTE, 0)
        } catch (e: Exception) { e.printStackTrace() }
        
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) { e.printStackTrace() }
        speechRecognizer = null
        
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        try {
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_ALARM, android.media.AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_DTMF, android.media.AudioManager.ADJUST_UNMUTE, 0)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getFullTranscript(): String = _transcript.value
    fun clearTranscript() { 
        _transcript.value = "" 
        _liveText.value = ""
    }
}
