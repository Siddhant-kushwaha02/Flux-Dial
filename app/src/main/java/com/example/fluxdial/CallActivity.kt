package com.example.fluxdial

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fluxdial.data.repository.CallRepository
import com.example.fluxdial.databinding.ActivityCallBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import java.util.*

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private val repository = CallRepository()
    private val db = Firebase.firestore
    private val uid = Firebase.auth.currentUser?.uid ?: ""

    private var callId: String = ""
    private var isCaller: Boolean = false
    private var remoteUid: String = ""

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private val rootEglBase = EglBase.create()

    private var isMuted = false
    private var isSpeakerOn = false
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        callId = intent.getStringExtra("callId") ?: ""
        isCaller = intent.getBooleanExtra("isCaller", false)

        requestAudioFocus()
        initWebRtc()
        setupSignaling()
        setupListeners()
        
        fetchRemoteUid()
    }

    private fun requestAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .build()
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun initWebRtc() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        binding.localVideoView.init(rootEglBase.eglBaseContext, null)
        binding.remoteVideoView.init(rootEglBase.eglBaseContext, null)
        binding.localVideoView.setZOrderMediaOverlay(true)

        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        ))

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val map = mapOf(
                    "sdp" to candidate.sdp,
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex
                )
                lifecycleScope.launch {
                    repository.saveIceCandidate(callId, uid, map)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                if (state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.FAILED) {
                    finish()
                }
            }

            override fun onAddStream(stream: MediaStream) {
                if (stream.videoTracks.isNotEmpty()) {
                    stream.videoTracks[0].addSink(binding.remoteVideoView)
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        // Create local audio track
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        peerConnection?.addTrack(audioTrack)
    }

    private fun fetchRemoteUid() {
        lifecycleScope.launch {
            val doc = db.collection("calls").document(callId).get().await()
            remoteUid = if (isCaller) doc.getString("calleeUid") ?: "" else doc.getString("callerUid") ?: ""
            if (remoteUid.isNotEmpty()) {
                listenForRemoteIceCandidates()
            }
        }
    }

    private fun setupSignaling() {
        if (isCaller) {
            val constraints = MediaConstraints()
            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    peerConnection?.setLocalDescription(this, desc)
                    lifecycleScope.launch {
                        repository.saveOffer(callId, desc.description)
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
            }, constraints)

            lifecycleScope.launch {
                repository.listenForAnswer(callId).collectLatest { sdp ->
                    if (sdp != null) {
                        peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {}
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                    }
                }
            }
        } else {
            lifecycleScope.launch {
                repository.listenForOffer(callId).collectLatest { sdp ->
                    if (sdp != null && peerConnection?.remoteDescription == null) {
                        peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                peerConnection?.createAnswer(object : SdpObserver {
                                    override fun onCreateSuccess(desc: SessionDescription) {
                                        peerConnection?.setLocalDescription(this, desc)
                                        lifecycleScope.launch {
                                            repository.saveAnswer(callId, desc.description)
                                        }
                                    }
                                    override fun onSetSuccess() {}
                                    override fun onCreateFailure(p0: String?) {}
                                    override fun onSetFailure(p0: String?) {}
                                }, MediaConstraints())
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
                    }
                }
            }
        }

        lifecycleScope.launch {
            repository.listenToCallStatus(callId).collectLatest { status ->
                if (status == "ended" || status == "declined") {
                    finish()
                }
            }
        }
    }

    private fun listenForRemoteIceCandidates() {
        lifecycleScope.launch {
            repository.listenForIceCandidates(callId, remoteUid).collectLatest { data ->
                val candidate = IceCandidate(
                    data["sdpMid"] as String,
                    (data["sdpMLineIndex"] as Long).toInt(),
                    data["sdp"] as String
                )
                peerConnection?.addIceCandidate(candidate)
            }
        }
    }

    private fun setupListeners() {
        binding.btnEndCall.setOnClickListener {
            lifecycleScope.launch {
                repository.updateCallStatus(callId, "ended")
                finish()
            }
        }

        binding.btnMute.setOnClickListener {
            isMuted = !isMuted
            audioTrack?.setEnabled(!isMuted)
            binding.btnMute.setImageResource(if (isMuted) R.drawable.ic_mic_off_white else R.drawable.ic_mic_white)
        }

        binding.btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = isSpeakerOn
            binding.btnSpeaker.setImageResource(if (isSpeakerOn) R.drawable.ic_volume_up_white else R.drawable.ic_volume_off_white)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        peerConnection?.close()
        peerConnectionFactory.dispose()
        rootEglBase.release()
        binding.localVideoView.release()
        binding.remoteVideoView.release()
        
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = false
    }
}
