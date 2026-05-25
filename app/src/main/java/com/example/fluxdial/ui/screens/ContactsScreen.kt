package com.example.fluxdial.ui.screens

import android.Manifest
import android.content.*
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.fluxdial.*
import com.example.fluxdial.telecom.CallManager

@Composable
fun ContactsScreen(navController: NavController) {
    val context = LocalContext.current
    var contactsList by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    // SIM Selection State
    var showSimSelection by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }
    val simAccounts = remember { getCallCapableSims(context) }

    val filteredContacts = remember(contactsList, searchQuery) {
        if (searchQuery.isBlank()) {
            contactsList
        } else {
            contactsList.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.phone.contains(searchQuery)
            }
        }
    }

    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedNumber by remember { mutableStateOf("") }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && selectedNumber.isNotEmpty()) {
            val defaultAccount = getDefaultPhoneAccount(context)
            if (simAccounts.size > 1 && defaultAccount == null) {
                pendingNumber = selectedNumber
                showSimSelection = true
            } else {
                placeCall(context, selectedNumber, false, defaultAccount)
            }
        }
    }

    LaunchedEffect(Unit) {
        contactsList = fetchContacts(context)
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

    // ROOT must be Box — not Column, not LazyColumn
    Box(modifier = Modifier.fillMaxSize().background(FluxBackground)) {

        // CHILD 1: Scrollable content — fills whole screen
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            item {
                // Show header OR spacer — never both
                if (selectedContact == null) {
                    // Normal header: top bar + search + priority
                    ContactsHeader(
                        navController = navController, 
                        contacts = contactsList,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it }
                    )
                } else {
                    // Reserve same height so list doesn't jump
                    Spacer(modifier = Modifier.height(140.dp))
                }
            }

            // ALL CONTACTS section header
            if (selectedContact == null) {
                item {
                    Text(
                        if (searchQuery.isBlank()) "ALL CONTACTS" else "SEARCH RESULTS",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                }
            }

            if (filteredContacts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (contactsList.isEmpty()) "No contacts found" else "No matching contacts", 
                            color = Color.Gray, 
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(filteredContacts) { contact ->
                    ContactListItem(
                        contact = contact,
                        isSelected = selectedContact?.id == contact.id,
                        onClick = {
                            if (selectedContact != null) {
                                selectedContact = null
                            } else {
                                navController.navigate("contact_detail/${contact.id}")
                            }
                        },
                        onLongPress = {
                            selectedContact = contact
                        },
                        onCallClick = {
                            selectedNumber = contact.phone
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CALL_PHONE
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                val defaultAccount = getDefaultPhoneAccount(context)
                                if (simAccounts.size > 1 && defaultAccount == null) {
                                    pendingNumber = contact.phone
                                    showSimSelection = true
                                } else {
                                    placeCall(context, contact.phone, false, defaultAccount)
                                }
                            } else {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        }
                    )
                }
            }
        }

        // CHILD 2: Floating 3-dot button — overlays the list
        if (selectedContact != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp, end = 12.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = { showContextMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Contact options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // CHILD 3: DropdownMenu — SIBLING of LazyColumn
        if (showContextMenu && selectedContact != null) {
            val contact = selectedContact!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 96.dp, end = 12.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = {
                        showContextMenu = false
                        selectedContact = null
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .width(220.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Call, contentDescription = null, tint = FluxPrimary)
                                Text("Call", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        },
                        onClick = {
                            showContextMenu = false
                            selectedContact = null
                            val defaultAccount = getDefaultPhoneAccount(context)
                            if (simAccounts.size > 1 && defaultAccount == null) {
                                pendingNumber = contact.phone
                                showSimSelection = true
                            } else {
                                placeCall(context, contact.phone, false, defaultAccount)
                            }
                        }
                    )
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = Color.White)
                                Text("Send Message", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        },
                        onClick = {
                            showContextMenu = false
                            selectedContact = null
                            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:${contact.phone}")
                            }
                            context.startActivity(smsIntent)
                        }
                    )
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(
                                    if (contact.isPriority) Icons.Default.StarBorder
                                    else Icons.Default.Star,
                                    contentDescription = null,
                                    tint = FluxPrimary
                                )
                                Text(
                                    if (contact.isPriority) "Remove Priority"
                                    else "Make Priority",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        },
                        onClick = {
                            showContextMenu = false
                            selectedContact = null
                            contact.isPriority = !contact.isPriority
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color.Red)
                                Text("Add to Favourites", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        },
                        onClick = {
                            showContextMenu = false
                            selectedContact = null
                            Toast.makeText(context, "${contact.name} added to favourites", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Gray)
                                Text("Copy Number", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        },
                        onClick = {
                            showContextMenu = false
                            selectedContact = null
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", contact.phone))
                            Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.Gray)
                                Text("Share Contact", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        },
                        onClick = {
                            showContextMenu = false
                            selectedContact = null
                            val shareIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${contact.name}: ${contact.phone}")
                            }, "Share contact")
                            context.startActivity(shareIntent)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                                Text("Delete Contact", style = MaterialTheme.typography.bodyMedium, color = Color.Red)
                            }
                        },
                        onClick = {
                            showContextMenu = false
                            selectedContact = null
                            Toast.makeText(context, "${contact.name} deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // CHILD 4: Tap-outside dismiss overlay
        if (selectedContact != null && !showContextMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        selectedContact = null
                    }
            )
        }
    }
}

@Composable
fun ContactsHeader(
    navController: NavController, 
    contacts: List<Contact>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    Column {
        FluxTopBar(title = "Flux Dial", onProfileClick = { navController.navigate("settings") })

        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(FluxCardBackground, RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { 
                    Text("Search contacts & AI summaries", color = Color.Gray, fontSize = 14.sp) 
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = FluxPrimary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            } else {
                Icon(Icons.Default.Mic, null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isEmpty()) {
            Text(
                "PRIORITY",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            val priority = contacts.filter { it.isPriority }
            if (priority.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No priority contacts", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(priority) { contact ->
                        PriorityContactCard(
                            name = contact.name,
                            sub = "Mobile",
                            brief = "Priority Contact",
                            onClick = {
                                navController.navigate("contact_detail/${contact.id}")
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "RECENT",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No recent contacts", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}
