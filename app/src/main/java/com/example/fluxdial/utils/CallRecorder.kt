package com.example.fluxdial.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.*

object CallRecorder {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentOutputUri: Uri? = null
    private var currentOutputFile: String? = null

    fun startRecording(context: Context, phoneNumber: String) {
        if (isRecording) return

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "call_${phoneNumber}_$timeStamp.m4a"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/FluxDialRecordings")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val audioUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        currentOutputUri = audioUri
        
        val pfd = audioUri?.let { resolver.openFileDescriptor(it, "w") }
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            // Using VOICE_COMMUNICATION for better call capture
            // Fallback to MIC if system blocks communication source
            try {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            } catch (_: Exception) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }

            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pfd != null) {
                setOutputFile(pfd.fileDescriptor)
            } else {
                // Fallback for older versions if insert failed or API < 29
                val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                if (!storageDir.exists()) storageDir.mkdirs()
                val file = java.io.File(storageDir, fileName)
                currentOutputFile = file.absolutePath
                setOutputFile(currentOutputFile)
            }
            prepare()
            try {
                start()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Recording failed to start", android.widget.Toast.LENGTH_SHORT).show()
                isRecording = false
                return
            }
        }
        
        isRecording = true
    }

    fun stopRecording(context: Context): String {
        if (!isRecording) return ""
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            isRecording = false
        }

        currentOutputUri?.let { uri ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, contentValues, null, null)
            }
            return uri.toString()
        }
        
        return currentOutputFile ?: ""
    }

    fun isRecordingActive(): Boolean = isRecording
}
