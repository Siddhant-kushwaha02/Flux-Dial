package com.example.fluxdial

import android.accounts.AccountManager
import android.app.role.RoleManager
import android.content.*
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.CallLog
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.TelecomManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.*
import androidx.navigation.compose.*
import com.example.fluxdial.telecom.CallManager
import com.example.fluxdial.utils.CallFrequencyHelper
import com.example.fluxdial.utils.FrequentContact
import com.example.fluxdial.ui.screens.ContactsScreen
import com.example.fluxdial.ui.screens.HistoryScreen
import kotlinx.coroutines.delay

// =====================================
// UTILS
// =====================================

data class Contact(
    val id: String,
    val name: String,
    val phone: String, // was number
    var isPriority: Boolean = false
)

data class CallRecord(
    val name: String?,
    val number: String,
    val type: String,
    val date: String,
    val duration: String
)

fun fetchContacts(context: Context): List<Contact> {
    val contacts = mutableListOf<Contact>()
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )

    cursor?.use {
        val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (it.moveToNext()) {
            val id = it.getString(idIndex)
            val name = it.getString(nameIndex)
            val number = it.getString(numberIndex)
            contacts.add(Contact(id, name, number))
        }
    }
    return contacts
}

fun fetchCallHistory(context: Context): List<CallRecord> {
    val history = mutableListOf<CallRecord>()
    val cursor = context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        null,
        null,
        null,
        CallLog.Calls.DATE + " DESC"
    )

    cursor?.use {
        val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
        val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
        val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
        val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

        while (it.moveToNext()) {
            val number = it.getString(numberIndex)
            val name = it.getString(nameIndex) ?: getContactName(context, number)
            val type = when (it.getInt(typeIndex)) {
                CallLog.Calls.INCOMING_TYPE -> "Incoming"
                CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                CallLog.Calls.MISSED_TYPE -> "Missed"
                else -> "Other"
            }
            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.getLong(dateIndex)))
            val duration = "${it.getInt(durationIndex)}s"
            history.add(CallRecord(name, number, type, date, duration))
        }
    }
    return history
}

fun placeCall(context: Context, number: String) {
    if (number.isEmpty()) return
    
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    val uri = Uri.fromParts("tel", number.trim(), null)
    
    try {
        telecomManager.placeCall(uri, Bundle())
    } catch (e: SecurityException) {
        e.printStackTrace()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getContactName(context: Context, phoneNumber: String): String? {
    if (phoneNumber.isEmpty()) return null
    val uri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(phoneNumber)
    )
    val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getString(0)
        }
    }
    return null
}

fun getDeviceAccounts(context: Context): List<Pair<String, String>> {
    val accounts = mutableListOf<Pair<String, String>>()
    val am = AccountManager.get(context)
    am.accounts.forEach { account ->
        if (account.type == "com.google") {
            accounts.add(account.name to account.type)
        }
    }
    accounts.add("Phone Storage" to "local")
    return accounts
}

fun saveContact(
    context: Context,
    firstName: String,
    lastName: String,
    phoneNumber: String,
    accountName: String?,
    accountType: String?
) {
    val ops = arrayListOf<ContentProviderOperation>()

    ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, if (accountType == "local") null else accountType)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, if (accountType == "local") null else accountName)
        .build())

    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
        .build())

    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
        .build())

    try {
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// =====================================
// COLORS & THEME
// =====================================

val FluxBackground = Color(0xFF000000)
val FluxSurface = Color(0xFF121212)
val FluxPrimary = Color(0xFF1E88E5)
val FluxCardBackground = Color(0xFF1A1A1A)

@Composable
fun FluxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FluxPrimary,
            background = FluxBackground,
            surface = FluxSurface,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}

class MainActivity : ComponentActivity() {
    private var navigationTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
        requestDefaultDialerRole()
        updateLockScreenVisibility()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        setContent {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.GET_ACCOUNTS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ -> }

            LaunchedEffect(Unit) {
                launcher.launch(permissionsToRequest.toTypedArray())
            }

            FluxTheme {
                val navController = rememberNavController()
                
                // Android 15 Reactive Navigation
                LaunchedEffect(navigationTarget) {
                    navigationTarget?.let { target ->
                        navController.navigate(target) {
                            launchSingleTop = true
                        }
                        navigationTarget = null // Reset after navigation
                    }
                }

                FluxDialApp(navController)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        val target = intent?.getStringExtra("navigate_to")
        if (target != null) {
            navigationTarget = target
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        updateLockScreenVisibility()
    }

    private fun updateLockScreenVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 123)
            }
        } else {
            val telecomManager = getSystemService(TelecomManager::class.java)
            if (telecomManager?.defaultDialerPackage != packageName) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                startActivity(intent)
            }
        }
    }
}

// =====================================
// NAVIGATION & APP SHELL
// =====================================

@Composable
fun FluxDialApp(navController: NavHostController = rememberNavController()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val screensWithBottomBar = listOf("dialer", "contacts", "history", "memory")
    val showBottomBar = currentRoute != null && screensWithBottomBar.any { currentRoute.startsWith(it) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                FluxBottomNavBar(navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dialer",
            modifier = Modifier.padding(padding)
        ) {
            composable("dialer") { DialerScreen(navController) }
            composable("contacts") { ContactsScreen(navController) }
            composable("history") { HistoryScreen(navController) }
            composable("memory") { MemoryScreen(navController) }
            composable("settings") { SettingsScreen(navController) }
            composable(
                route = "contact_detail/{contactId}",
                arguments = listOf(navArgument("contactId") { type = NavType.StringType })
            ) { backStackEntry ->
                val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
                ContactDetailScreen(contactId = contactId, navController = navController)
            }
            composable("livecall") { LiveCallScreen(navController) }
        }
    }
}

@Composable
fun FluxBottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = Color.Black.copy(alpha = 0.9f),
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple("dialer", "Dialer", Icons.Default.Dialpad),
            Triple("contacts", "Contacts", Icons.Default.PersonSearch),
            Triple("history", "History", Icons.Default.History),
            Triple("memory", "AI Memory", Icons.Default.Psychology)
        )

        items.forEach { (route, label, icon) ->
            val isSelected = currentRoute == route
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = if (isSelected) Modifier
                            .background(FluxPrimary.copy(alpha = 0.2f), CircleShape)
                            .padding(8.dp) else Modifier
                    )
                },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FluxPrimary,
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = FluxPrimary,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

// =====================================
// COMMON UI COMPONENTS
// =====================================

@Composable
fun FluxTopBar(
    title: String,
    onProfileClick: () -> Unit = {},
    showLogo: Boolean = true,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(8.dp))
            } else if (showLogo) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
                    .clickable { onProfileClick() }
            ) {
                // Profile Image Placeholder
            }
        }
    }
}

@Composable
fun FluxSearchBar(placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp)
            .background(FluxCardBackground, RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
        Spacer(modifier = Modifier.width(12.dp))
        Text(placeholder, color = Color.Gray, modifier = Modifier.weight(1f))
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FluxPrimary, modifier = Modifier.size(20.dp))
    }
}

// =====================================
// DIALER SCREEN (Image 1)
// =====================================

@Composable
fun DialerScreen(navController: NavController) {
    var number by remember { mutableStateOf("") }
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }

    LaunchedEffect(Unit) {
        contacts = fetchContacts(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FluxBackground)
    ) {
        FluxTopBar(
            title = "",
            showLogo = false,
            actions = {
                IconButton(onClick = { /* Add contact */ }) { Icon(Icons.Default.Add, null, tint = Color.White) }
                IconButton(onClick = { /* Refresh/Rotate? */ }) { Icon(Icons.Default.Sync, null, tint = Color.White) }
                IconButton(onClick = { /* Search */ }) { Icon(Icons.Default.Search, null, tint = Color.White) }
                IconButton(onClick = { navController.navigate("settings") }) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
            }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Dialer Grid and matching contacts
        DialerKeypad(
            initialNumber = number,
            contacts = contacts,
            onNumberChange = { number = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DialerKeypad(initialNumber: String, contacts: List<Contact>, onNumberChange: (String) -> Unit) {
    var number by remember(initialNumber) { mutableStateOf(initialNumber) }
    val context = LocalContext.current

    val filteredContacts = remember(number, contacts) {
        if (number.isEmpty()) emptyList()
        else contacts.filter { it.phone.replace("-", "").contains(number) }
    }
    
    // Sync internal state with external when external changes
    LaunchedEffect(number) {
        onNumberChange(number)
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            placeCall(context, number)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Matching contacts list
        if (filteredContacts.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredContacts) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { number = contact.phone.replace("-", "") }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(contact.name.firstOrNull()?.toString() ?: "", color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = contact.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = contact.phone,
                            color = FluxPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Invisible Search Bar / Number Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 48.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        val keys = listOf(
            listOf("1" to " ", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )
        
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (digit, letters) ->
                    DialerButton(digit, letters) { number += digit }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Call
            IconButton(
                onClick = { /* Video Call */ },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video Call",
                    tint = Color.Green.copy(alpha = 0.8f),
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Call Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32)) // Green
                    .clickable { 
                        if (number.isNotEmpty()) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                placeCall(context, number)
                            } else {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            
            // Backspace
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { if (number.isNotEmpty()) number = number.dropLast(1) },
                        onLongClick = { number = "" }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun DialerButton(digit: String, letters: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(72.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(digit, fontSize = 28.sp, color = Color.White)
        if (letters.isNotEmpty()) {
            Text(letters, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveContactBottomSheet(
    phoneNumber: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Pair<String, String>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    val accounts = remember { getDeviceAccounts(context) }
    var selectedAccount by remember { mutableStateOf(accounts.first()) }
    var expanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = FluxSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text("Create New Contact", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Phone Number", fontSize = 12.sp, color = Color.Gray)
            Text(phoneNumber, fontSize = 18.sp, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))

            Text("Saving to", fontSize = 12.sp, color = Color.Gray)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = FluxCardBackground)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedAccount.first, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(FluxCardBackground)
                ) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.first, color = Color.White) },
                            onClick = {
                                selectedAccount = account
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { onSave(firstName, lastName, selectedAccount) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FluxPrimary),
                enabled = firstName.isNotBlank()
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PriorityContactCard(name: String, sub: String, brief: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = FluxCardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                    Text(name.firstOrNull()?.toString() ?: "", color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(sub, color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.MoreHoriz, null, tint = Color.Gray)
            }
            if (brief.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(brief, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactListItem(
    contact: Contact,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onCallClick: () -> Unit
) {
    val bgColor = if (isSelected)
        Color.White.copy(alpha = 0.1f)
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongPress() }
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.name.firstOrNull()?.toString() ?: "", color = Color.White)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, color = Color.White)
            Text(contact.phone, color = Color.Gray, fontSize = 12.sp)
        }
        IconButton(onClick = onCallClick) {
            Icon(Icons.Default.Call, contentDescription = "Call", tint = FluxPrimary)
        }
    }
}

// =====================================
// CONTACT DETAIL SCREEN (Image 6)
// =====================================

@Composable
fun ContactDetailScreen(contactId: String, navController: NavController) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    LaunchedEffect(Unit) {
        contacts = fetchContacts(context)
    }
    val contact = contacts.find { it.id == contactId }
    val contactNumber = contact?.phone ?: "No Number"
    val contactName = contact?.name ?: "Contact Details"
    
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            placeCall(context, contactNumber)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(FluxBackground).verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(contactName, color = Color.White, fontSize = 18.sp)
            Row {
                Icon(Icons.Default.StarOutline, null, tint = Color.White)
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Default.MoreVert, null, tint = Color.White)
            }
        }
        
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                Text(contactName.firstOrNull()?.toString() ?: "", color = Color.White, fontSize = 40.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(contactName, fontSize = 28.sp, color = Color.White)
            Text(contactNumber, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { 
                        if (ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CALL_PHONE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            placeCall(context, contactNumber)
                        } else {
                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FluxPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Call, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Call")
                }
                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FluxCardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SectionCard("AI QUICK BRIEF", "No AI brief available for this contact yet.")
        
        Text("Insights & Reminders", modifier = Modifier.padding(16.dp), color = Color.White, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = FluxCardBackground)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("No upcoming insights or reminders.", color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SectionCard("Relationship Stat", "No relationship data available.")
    }
}

@Composable
fun SectionCard(header: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = FluxCardBackground)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = FluxPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(header, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(body, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

// =====================================
// LIVE CALL SCREEN (Image 3)
// =====================================

@Composable
fun LiveCallScreen(navController: NavController) {
    val context = LocalContext.current
    val call by CallManager.currentCall.collectAsState()
    val callState by CallManager.callState.collectAsState()
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    var elapsedTime by remember { mutableStateOf("00:00") }
    
    LaunchedEffect(callState) {
        while (callState == Call.STATE_ACTIVE) {
            elapsedTime = CallManager.getCallDuration()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(FluxBackground).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Default.Call, null, tint = Color.Gray)
            Text("Flux Dial", color = Color.Gray)
            Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.DarkGray))
        Spacer(modifier = Modifier.height(16.dp))
        
        val number = call?.details?.handle?.schemeSpecificPart ?: "Unknown"
        val name = remember(number) { getContactName(context, number) ?: "Active Call" }
        
        Text(name, fontSize = 32.sp, color = Color.White)
        Text(elapsedTime, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FluxCardBackground)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE SUMMARY", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.AutoAwesome, null, tint = FluxPrimary, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Discussing Project Nebula timelines...", color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            var isMuted by remember { mutableStateOf(audioManager.isMicrophoneMute) }
            CallActionItem(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                onClick = {
                    isMuted = !isMuted
                    audioManager.isMicrophoneMute = isMuted
                }
            )
            CallActionItem(Icons.Default.Dialpad, "Keypad")
            
            var isSpeaker by remember { mutableStateOf(audioManager.isSpeakerphoneOn) }
            CallActionItem(
                icon = if (isSpeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                label = "Speaker",
                onClick = {
                    isSpeaker = !isSpeaker
                    audioManager.isSpeakerphoneOn = isSpeaker
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FluxPrimary),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("SMART ACTIONS", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallSecondaryAction(Icons.Default.Videocam, "AUDIO -> VIDEO", Modifier.weight(1f))
            CallSecondaryAction(Icons.Default.GraphicEq, "AI CLARITY", Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        IconButton(
            onClick = { 
                CallManager.endCall()
                navController.popBackStack()
            },
            modifier = Modifier.size(64.dp).background(Color.Red, CircleShape)
        ) {
            Icon(Icons.Default.CallEnd, null, tint = Color.White)
        }
    }
}

@Composable
fun CallActionItem(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun CallSecondaryAction(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Button(
        onClick = { },
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = FluxCardBackground),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp)
    }
}

// =====================================
// SETTINGS SCREEN (Image 2)
// =====================================

@Composable
fun SettingsScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(FluxBackground).verticalScroll(rememberScrollState())) {
        FluxTopBar(
            title = "Settings",
            showLogo = false,
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
            }
        )
        
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = FluxCardBackground), shape = RoundedCornerShape(24.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.DarkGray))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("User", fontWeight = FontWeight.Bold)
                    Text("No email linked", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    SuggestionChip(onClick = {}, label = { Text("AI Ready") }, icon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp)) })
                }
                Icon(Icons.Default.Edit, null, tint = Color.Gray)
            }
        }
        
        SettingsSection("INTELLIGENCE") {
            SettingsItem(Icons.Default.Psychology, "AI Memory & Summaries", "Configure call insights")
            SettingsItem(Icons.Default.RecordVoiceOver, "Voice Assistant", "AI integration")
            SettingsItem(Icons.Default.GraphicEq, "Audio Processing", "Noise cancellation & clarity")
        }
        
        SettingsSection("PRIVACY & DEVICE") {
            SettingsItem(Icons.Default.Shield, "Privacy Controls", "Manage local processing")
            SettingsItem(Icons.Default.RadioButtonChecked, "Recording Preferences", "Auto-record & storage", "On-Device")
        }
        
        SettingsSection("GENERAL") {
            SettingsItem(Icons.Default.Palette, "Theme", "Cinematic Dark")
            SettingsItem(Icons.AutoMirrored.Filled.HelpOutline, "Help & Support", "")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FluxCardBackground),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(title, color = FluxPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = FluxCardBackground)) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, sub: String, extra: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).background(Color.DarkGray.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White)
            if (sub.isNotEmpty()) {
                Text(sub, color = Color.Gray, fontSize = 12.sp)
            }
        }
        if (extra.isNotEmpty()) {
            Text(extra, color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
    }
}

// =====================================
// AI MEMORY SCREEN (Image 7)
// =====================================

@Composable
fun MemoryScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(FluxBackground)) {
        FluxTopBar(
            title = "AI Memory",
            showLogo = false,
            onProfileClick = { navController.navigate("settings") },
            navigationIcon = { Icon(Icons.Default.Menu, null, tint = Color.White) }
        )
        
        FluxSearchBar(placeholder = "Ask AI: Search your memory...")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(onClick = {}, label = { Text("Action Items") }, icon = { Icon(Icons.Default.Check, null) })
            SuggestionChip(onClick = {}, label = { Text("Recent Calls") }, icon = { Icon(Icons.Default.Schedule, null) })
        }
        
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = FluxPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Action Items Extracted", color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No action items yet", color = Color.Gray, fontSize = 12.sp)
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timeline, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Conversation Memory", color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No conversation history", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
