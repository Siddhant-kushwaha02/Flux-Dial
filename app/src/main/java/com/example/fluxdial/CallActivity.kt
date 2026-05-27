package com.example.fluxdial

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fluxdial.data.repository.CallRepository
import com.example.fluxdial.databinding.ActivityCallBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.getstream.webrtc.android.PeerConnectionFactory
import io.getstream.webrtc.android.WebRtcSession
import io.getstream.webrtc.android.model.IceCandidate
import io.getstream.webrtc.android.model.SessionDescription
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private val repository = CallRepository()
    private val db = Firebase.firestore
    private val uid = Firebase.auth.currentUser?.uid ?: ""

    private var callId: String = ""
    private var isCaller: Boolean = false

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var session: WebRtcSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        callId = intent.getStringExtra("callId") ?: ""
        isCaller = intent.getBooleanExtra("isCaller", false)

        initWebRtc()
        setupSignaling()
        setupListeners()
    }

    private fun initWebRtc() {
        peerConnectionFactory = PeerConnectionFactory(this)
        
        // Setup local and remote renderers
        binding.localVideoView.init(peerConnectionFactory.eglContext, null)
        binding.remoteVideoView.init(peerConnectionFactory.eglContext, null)

        session = peerConnectionFactory.createWebRtcSession(
            videoEnabled = true,
            audioEnabled = true
        )

        // Local video track
        session.localVideoTrack?.addSink(binding.localVideoView)
        
        // Remote video track
        session.remoteVideoTrack?.addSink(binding.remoteVideoView)
    }

    private fun setupSignaling() {
        if (isCaller) {
            lifecycleScope.launch {
                val offer = session.createOffer().getOrThrow()
                session.setLocalDescription(offer)
                
                db.collection("calls").document(callId)
                    .update("offer", mapOf("sdp" to offer.description, "type" to "offer"))
                    .await()
            }
        }

        // Listen for Answer
        db.collection("calls").document(callId)
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getString("status")
                if (status == "ended" || status == "declined") {
                    finish()
                }

                if (isCaller) {
                    val answer = snapshot?.get("answer") as? Map<*, *>
                    if (answer != null) {
                        val sdp = answer["sdp"] as String
                        session.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, sdp))
                    }
                } else {
                    val offer = snapshot?.get("offer") as? Map<*, *>
                    if (offer != null && session.remoteDescription == null) {
                        val sdp = offer["sdp"] as String
                        lifecycleScope.launch {
                            session.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdp))
                            val answer = session.createAnswer().getOrThrow()
                            session.setLocalDescription(answer)
                            db.collection("calls").document(callId)
                                .update("answer", mapOf("sdp" to answer.description, "type" to "answer"))
                        }
                    }
                }
            }

        // ICE Candidates exchange
        session.iceCandidates.onEach { candidate ->
            val candidateMap = mapOf(
                "sdp" to candidate.sdp,
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex
            )
            db.collection("calls").document(callId)
                .collection("iceCandidates").document(uid)
                .collection("candidates").add(candidateMap)
        }

        // Listen for remote ICE candidates
        val remoteUid = if (isCaller) "calleeUid" else "callerUid" // Simplified, needs actual remote UID
        // In a real app, you'd fetch the remote UID from the call document
        
        // Monitor both caller and callee candidates and filter out self
        db.collection("calls").document(callId)
            .collection("iceCandidates")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    val otherUid = change.document.id
                    if (otherUid != uid) {
                        change.document.reference.collection("candidates")
                            .addSnapshotListener { candSnapshots, _ ->
                                candSnapshots?.documentChanges?.forEach { candChange ->
                                    val data = candChange.document.data
                                    val iceCandidate = IceCandidate(
                                        data["sdp"] as String,
                                        data["sdpMid"] as String,
                                        (data["sdpMLineIndex"] as Long).toInt()
                                    )
                                    session.addIceCandidate(iceCandidate)
                                }
                            }
                    }
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
            val enabled = session.audioEnabled
            session.audioEnabled = !enabled
            // Update UI icon if needed
        }

        binding.btnSpeaker.setOnClickListener {
            // Toggle speaker using AudioManager
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session.dispose()
        binding.localVideoView.release()
        binding.remoteVideoView.release()
    }
}
