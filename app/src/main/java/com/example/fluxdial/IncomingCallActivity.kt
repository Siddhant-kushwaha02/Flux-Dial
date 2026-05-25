package com.example.fluxdial

import android.app.KeyguardManager
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fluxdial.telecom.CallManager
import com.example.fluxdial.telecom.FluxRingtoneManager

class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show over lockscreen and wake up screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager != null) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
        
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )

        val callerName = intent?.getStringExtra("caller_name")
            ?.takeIf { it.isNotBlank() && (it != "Unknown") }
            ?: CallManager.getCurrentCallerName()
                ?.takeIf { it.isNotBlank() }
            ?: "Unknown"

        val callerNumber = intent?.getStringExtra("caller_number")
            ?: CallManager.getCurrentCallerNumber() ?: ""
        
        val isVideoCall = intent?.getBooleanExtra("is_video", false) ?: CallManager.isVideoCall()

        setContent {
            FluxTheme {
                IncomingCallScreen(
                    callerName = callerName,
                    callerNumber = callerNumber,
                    isVideoCall = isVideoCall,
                    onAnswer = {
                        FluxRingtoneManager.stopRinging()
                        CallManager.acceptCall()
                        // navigate to live call screen
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("navigate_to", "livecall")
                        }
                        startActivity(intent)
                        finish()
                    },
                    onDecline = {
                        FluxRingtoneManager.stopRinging()
                        CallManager.rejectCall()
                        finish()
                    },
                    onCallEnded = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FluxRingtoneManager.stopRinging()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    // Silence the ringing only — do NOT end the call
                    FluxRingtoneManager.silenceRinging()
                    
                    // Release audio focus
                    val am = getSystemService(AUDIO_SERVICE) as AudioManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // In silence mode, we can just abandon the generic focus
                        // The original request is not easily available here
                        @Suppress("DEPRECATION")
                        am.abandonAudioFocus(null)
                    } else {
                        @Suppress("DEPRECATION")
                        am.abandonAudioFocus(null)
                    }
                    return true // consume the event, don't change volume level
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String,
    callerNumber: String,
    isVideoCall: Boolean = false,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onCallEnded: () -> Unit
) {
    val callState by CallManager.callState.collectAsState()
    
    // Auto-finish activity if call is disconnected
    LaunchedEffect(callState) {
        if (callState == Call.STATE_DISCONNECTED) {
            onCallEnded()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color.Black)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // Caller Avatar Placeholder
            Box(contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(160.dp).border(1.dp, FluxPrimary.copy(alpha = 0.3f), CircleShape))
                Box(modifier = Modifier.size(130.dp).clip(CircleShape).background(Color.DarkGray))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = when (callState) {
                    Call.STATE_RINGING -> if (isVideoCall) "Incoming Video Call..." else "Incoming Call..."
                    Call.STATE_DIALING, Call.STATE_CONNECTING -> if (isVideoCall) "Outgoing Video Call..." else "Outgoing Call..."
                    else -> if (isVideoCall) "Active Video Call" else "Active Call"
                },
                fontSize = 20.sp,
                color = if (isVideoCall) Color.Green else FluxPrimary
            )
            
            if (isVideoCall) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "VIDEO CALL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            Text(
                text = callerName,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (callerNumber.isNotEmpty()) {
                Text(
                    text = callerNumber,
                    fontSize = 18.sp,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // AI Spam Status Placeholder
            SuggestionChip(
                onClick = {},
                label = { Text("AI: Safe Call") },
                icon = { Icon(Icons.Default.Shield, null, modifier = Modifier.size(16.dp)) }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            if (callState == Call.STATE_RINGING) {
                RingingActions(onAnswer, onDecline)
            } else {
                ActiveCallActions()
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun RingingActions(onAnswer: () -> Unit, onDecline: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { CallManager.performAiAnswer() },
            colors = ButtonDefaults.buttonColors(containerColor = FluxCardBackground),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, FluxPrimary.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("AI ANSWER & SCREEN")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallActionButton(
                icon = Icons.Default.CallEnd,
                color = Color.Red,
                label = "Reject",
                onClick = onDecline
            )
            
            CallActionButton(
                icon = Icons.Default.Call,
                color = Color.Green,
                label = "Accept",
                onClick = onAnswer
            )
        }
    }
}

@Composable
fun ActiveCallActions() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = {}) { Icon(Icons.Default.MicOff, null, tint = Color.White) }
            IconButton(onClick = {}) { Icon(Icons.Default.Dialpad, null, tint = Color.White) }
            IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White) }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        FloatingActionButton(
            onClick = { CallManager.disconnectCall() },
            containerColor = Color.Red,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(Icons.Default.CallEnd, null, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = color,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}
