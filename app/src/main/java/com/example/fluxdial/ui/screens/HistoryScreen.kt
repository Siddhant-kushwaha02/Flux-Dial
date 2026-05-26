package com.example.fluxdial.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog as SystemCallLog
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.fluxdial.*
import com.example.fluxdial.data.CallLog
import com.example.fluxdial.data.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    var history by remember { mutableStateOf<List<CallLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // SIM Selection State
    var showSimSelection by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }
    val simAccounts = remember { getCallCapableSims(context) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            history = withContext(Dispatchers.IO) {
                fetchRealCallLogs(context)
            }
            isLoading = false
        } else {
            launcher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }
    
    if (showSimSelection) {
        SimSelectionDialog(
            sims = simAccounts,
            onSimSelected = { sim ->
                placeCall(context, pendingNumber, false, sim.handle)
                showSimSelection = false
            },
            onDismiss = { showSimSelection = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(FluxBackground)) {
        FluxTopBar(title = "History", showLogo = false, onProfileClick = { navController.navigate("settings") })
        
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = FluxPrimary
                )
            } else if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (hasPermission) "No call history available" else "Permission required to view history",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(history) { callLog ->
                        HistoryRow(
                            callLog = callLog, 
                            navController = navController, 
                            context = context,
                            onCallClick = { number ->
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                    val defaultAccount = getDefaultPhoneAccount(context)
                                    if (simAccounts.size > 1 && defaultAccount == null) {
                                        pendingNumber = number
                                        showSimSelection = true
                                    } else {
                                        placeCall(context, number, false, defaultAccount)
                                    }
                                } else {
                                    // Should ideally handle permission here or in parent
                                    placeCall(context, number, false)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun fetchRealCallLogs(context: Context): List<CallLog> {
    val historyList = mutableListOf<CallLog>()
    try {
        val cursor = context.contentResolver.query(
            SystemCallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            SystemCallLog.Calls.DATE + " DESC LIMIT 500"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(SystemCallLog.Calls._ID)
            val nameIndex = it.getColumnIndex(SystemCallLog.Calls.CACHED_NAME)
            val numberIndex = it.getColumnIndex(SystemCallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndex(SystemCallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(SystemCallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(SystemCallLog.Calls.DURATION)
            val accountIdIndex = it.getColumnIndex(SystemCallLog.Calls.PHONE_ACCOUNT_ID)

            // Validate that we have the minimum required columns
            if (numberIndex == -1 || typeIndex == -1 || dateIndex == -1) return emptyList()

            while (it.moveToNext()) {
                val id = if (idIndex != -1) it.getString(idIndex) ?: "" else ""
                val number = it.getString(numberIndex) ?: ""
                val cachedName = if (nameIndex != -1) it.getString(nameIndex) else null
                val accountId = if (accountIdIndex != -1) it.getString(accountIdIndex) else null
                
                val simLabel = getSimLabel(context, accountId)
                
                val type = when (it.getInt(typeIndex)) {
                    SystemCallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    SystemCallLog.Calls.MISSED_TYPE -> CallType.MISSED
                    else -> CallType.INCOMING
                }
                
                val dateMillis = it.getLong(dateIndex)
                val timestamp = formatTimestamp(dateMillis)
                
                val durationSeconds = if (durationIndex != -1) it.getInt(durationIndex) else 0
                val duration = formatDuration(durationSeconds)

                val contact = resolveContact(context, number, cachedName)

                historyList.add(CallLog(id, contact, type, duration, timestamp, simLabel = simLabel))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return historyList
}

private fun getSimLabel(context: Context, accountId: String?): String? {
    if (accountId == null) return null
    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    return try {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val subs = subscriptionManager.activeSubscriptionInfoList
            val sub = subs?.find { it.subscriptionId.toString() == accountId || it.iccId == accountId }
            if (sub != null) {
                "Sim ${sub.simSlotIndex + 1}"
            } else null
        } else null
    } catch (e: Exception) {
        null
    }
}

private fun formatTimestamp(millis: Long): String {
    return try {
        val date = Date(millis)
        val now = Calendar.getInstance()
        val then = Calendar.getInstance()
        then.time = date
        
        val pattern = if (now[Calendar.YEAR] == then[Calendar.YEAR]) {
            if (now[Calendar.DAY_OF_YEAR] == then[Calendar.DAY_OF_YEAR]) {
                "'Today', h:mm a"
            } else if ((now[Calendar.DAY_OF_YEAR] - then[Calendar.DAY_OF_YEAR]) == 1) {
                "'Yesterday', h:mm a"
            } else {
                "MMM d, h:mm a"
            }
        } else {
            "MMM d yyyy, h:mm a"
        }
        
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    } catch (e: Exception) {
        "Unknown time"
    }
}

private fun formatDuration(seconds: Int): String {
    return when {
        seconds <= 0 -> "0s"
        seconds < 60 -> "${seconds}s"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }
}

private fun resolveContact(context: Context, number: String, cachedName: String?): Contact {
    if (number.isBlank()) return Contact("", cachedName ?: "Unknown", "")
    
    val uri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(number)
    )
    val projection = arrayOf(
        ContactsContract.PhoneLookup._ID,
        ContactsContract.PhoneLookup.DISPLAY_NAME
    )
    
    return try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getString(0) ?: ""
                val name = cursor.getString(1) ?: cachedName ?: number
                Contact(id, name, number)
            } else {
                // Unknown number - use cached name (from incoming call) and number in brackets
                val displayName = if (cachedName != null && cachedName != number) {
                    "$cachedName ($number)"
                } else {
                    number
                }
                Contact("", displayName, number)
            }
        } ?: Contact("", if (cachedName != null && cachedName != number) "$cachedName ($number)" else number, number)
    } catch (_: Exception) {
        Contact("", if (cachedName != null && cachedName != number) "$cachedName ($number)" else number, number)
    }
}

@Composable
fun HistoryRow(callLog: CallLog, navController: NavController, context: Context, onCallClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (callLog.contact.id.isNotEmpty()) {
                    navController.navigate("contact_detail/${callLog.contact.id}")
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: colored arrow icon in circle
        CallTypeIcon(callType = callLog.type)

        Spacer(modifier = Modifier.width(12.dp))

        // Middle: contact name + call details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = callLog.contact.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (callLog.type == CallType.MISSED)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${callLog.type.name.lowercase().replaceFirstChar { it.uppercase() }} • ${callLog.duration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (callLog.simLabel != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color.Gray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = callLog.simLabel,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Right: relative time
        Text(
            text = callLog.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Call back button
        IconButton(
            onClick = { onCallClick(callLog.contact.phone) }
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Call back",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CallTypeIcon(callType: CallType) {
    val (icon, color) = when (callType) {
        CallType.INCOMING -> Pair(
            Icons.AutoMirrored.Filled.CallReceived,
            Color(0xFF9E9E9E)  // grey
        )
        CallType.OUTGOING -> Pair(
            Icons.AutoMirrored.Filled.CallMade,
            Color(0xFF4CAF50)  // green
        )
        CallType.MISSED -> Pair(
            Icons.AutoMirrored.Filled.CallMissed,
            Color(0xFFF44336)  // red
        )
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = callType.name,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}
