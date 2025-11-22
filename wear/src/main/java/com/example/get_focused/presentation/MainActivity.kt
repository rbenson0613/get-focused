package com.example.get_focused.presentation

import android.app.NotificationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.get_focused.sync.SyncedEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.provider.Settings
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.get_focused.presentation.theme.Get_FocusedTheme
import com.example.get_focused.presentation.ui.EventListScreen
import com.example.get_focused.presentation.ui.SignInScreen
import com.example.get_focused.presentation.ui.UiEvent
import com.example.get_focused.sync.DataSyncService
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import android.net.Uri

// Imports for layout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

// Imports for new icons
import androidx.compose.material.icons.filled.Check

// Imports for the fixes
import androidx.compose.ui.unit.dp  // <-- FIX 1: For using 18.dp

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private val TAG = "MainActivityWear"
    private val viewModel: CountdownViewModel by viewModels()

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.checkSignInStatus()
        }
    }

    private val syncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DataSyncService.ACTION_SYNC_EVENTS) {
                viewModel.checkSignInStatus()
            }
        }
    }

    // NEW: Receiver for event start broadcasts
    private val eventStartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == EventStartReceiver.ACTION_EVENT_STARTED) {
                val eventTitle = intent.getStringExtra("eventTitle") ?: return
                val duration = intent.getLongExtra("duration", 0L)

                Log.d(TAG, "Event started broadcast received: $eventTitle")

                // Mark as opened since we're handling it
                lifecycleScope.launch {
                    applicationContext.notificationDataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("event_opened")] = true
                    }
                }

                // Trigger countdown in ViewModel
                viewModel.forceCheckTimerState()
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
            } else {
                Log.w(TAG, "Notification permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        checkFullScreenIntentPermission()
        requestNotificationPermission()

        // Register both receivers
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(syncReceiver, IntentFilter(DataSyncService.ACTION_SYNC_EVENTS))

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(eventStartReceiver, IntentFilter(EventStartReceiver.ACTION_EVENT_STARTED))

        setContent {
            val appState by viewModel.appState.collectAsState()

            Get_FocusedTheme {
                WearApp(
                    appState = appState,
                    onSignInClick = {
                        val signInIntent = Intent(this, AuthActivity::class.java)
                        authLauncher.launch(signInIntent)
                    },
                    onEventClick = { event ->
                        viewModel.startCountdownForEvent(event)
                    },
                    onStopClick = {
                        viewModel.stopCountdown()
                    },
                    onTestAlarmClick = {
                        viewModel.testAlarmNow()
                    }
                )
            }
        }

        // Check if launched by alarm or notification
        handleAutoStartIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.d(TAG, "Showing notification permission rationale")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Log.d(TAG, "Requesting notification permission")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                // Show dialog explaining why we need this permission
                android.app.AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("This app needs permission to show full-screen alarms when events start. Please enable it in settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to app settings
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    private fun requestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open full-screen intent settings", e)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called with extras: ${intent.extras?.keySet()?.joinToString()}")
        setIntent(intent)
        handleAutoStartIntent(intent)
    }

    private fun handleAutoStartIntent(intent: Intent?) {
        val isAutoStart = intent?.getBooleanExtra("auto_start_event", false) ?: false
        val markAsOpened = intent?.getBooleanExtra("mark_as_opened", false) ?: false

        Log.d(TAG, "handleAutoStartIntent: isAutoStart=$isAutoStart, markAsOpened=$markAsOpened")

        if (markAsOpened) {
            lifecycleScope.launch {
                applicationContext.notificationDataStore.edit { prefs ->
                    prefs[booleanPreferencesKey("event_opened")] = true
                }
                Log.d(TAG, "✅ Marked as opened from notification tap")
            }
        }

        if (isAutoStart) {
            Log.d(TAG, "Auto-start event detected - forcing timer state check")
            viewModel.forceCheckTimerState()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        Wearable.getDataClient(this).addListener(this)

        handleAutoStartIntent(intent)
        checkPendingDataItems()
    }

    override fun onPause() {
        Log.d(TAG, "onPause called")
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == "/sync-events") {

                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    handleSyncDataMap(dataMapItem.dataMap)
                }
            }
        } finally {
            dataEvents.release()
        }
    }

    private fun handleSyncDataMap(dataMap: DataMap) {
        val timestamp = dataMap.getLong("timestamp", 0L)
        val eventCount = dataMap.getInt("event_count", -1)
        val eventsJson = dataMap.getString("events_json")

        Log.d(TAG, "Received sync data: timestamp=$timestamp, eventCount=$eventCount")

        if (eventsJson != null) {
            try {
                val syncedEvents = Json.decodeFromString<List<SyncedEvent>>(eventsJson)
                Log.d(TAG, "Successfully parsed ${syncedEvents.size} events")

                val uiEvents = syncedEvents.mapNotNull { event ->
                    val endTime = event.endTime
                    if (endTime != null) {
                        UiEvent(
                            title = event.title,
                            startTimeMillis = event.startTime,
                            endTimeMillis = endTime
                        )
                    } else {
                        null
                    }
                }

                val now = System.currentTimeMillis()
                val activeEvent = uiEvents.firstOrNull { event ->
                    now >= event.startTimeMillis && now < event.endTimeMillis
                }

                if (activeEvent != null) {
                    Log.d(TAG, "Found active event: ${activeEvent.title}")
                    viewModel.startCountdownForEvent(activeEvent)
                } else {
                    Log.d(TAG, "No active event found. Updating event list")
                    viewModel.updateEventList(uiEvents)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse events JSON", e)
            }
        }
    }

    private fun checkPendingDataItems() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataItemBuffer = Wearable.getDataClient(this@MainActivity).getDataItems().await()
                try {
                    for (item in dataItemBuffer) {
                        if (item.uri.path == "/sync-events") {
                            val dataMapItem = DataMapItem.fromDataItem(item)
                            handleSyncDataMap(dataMapItem.dataMap)
                        }
                    }
                } finally {
                    dataItemBuffer.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkPendingDataItems failed", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(eventStartReceiver)
        Log.d(TAG, "onDestroy called")
    }
}

@Composable
fun WearApp(
    appState: AppState,
    onSignInClick: () -> Unit,
    onEventClick: (UiEvent) -> Unit,
    onStopClick: () -> Unit,
    onTestAlarmClick: () -> Unit
) {
    when (appState) {
        is AppState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AppState.NeedsSignIn -> {
            SignInScreen(onSignInClick = onSignInClick)
        }
        is AppState.ShowEventList -> {
            EventListScreen(
                events = appState.events,
                onEventClick = onEventClick,
                onTestAlarmClick = onTestAlarmClick
            )
        }
        is AppState.WaitingForEvent -> {
            CountdownScreen(
                progress = 1f,
                time = "Waiting...",
                currentTime = appState.currentTime,
                eventTitle = appState.eventTitle,
                onStopClick = onStopClick
            )
        }
        is AppState.ShowCountdown -> {
            CountdownScreen(
                progress = appState.progress,
                time = appState.time,
                currentTime = appState.currentTime,
                eventTitle = appState.eventTitle,
                onStopClick = onStopClick
            )
        }
    }
}

@Composable
fun CountdownScreen(
    progress: Float,
    time: String,
    currentTime: String,
    eventTitle: String,
    onStopClick: () -> Unit // Kept in signature for compatibility, but unused in layout
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 1. Circular progress bar
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            indicatorColor = Color(0xFF00BCD4),
            trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f)
        )

        // Inner Box for content
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 2. Event Title at the Top (Large)
            Text(
                text = eventTitle,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 30.dp, start = 10.dp, end = 10.dp), // Push down from top bezel
                textAlign = TextAlign.Center,
                fontSize = 22.sp, // Larger font
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )

            // 3. Icon in the Center (Large)
            Icon(
                imageVector = Icons.Default.Coffee,
                contentDescription = "Event Icon",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp), // Much larger icon
                tint = Color(0xFF00BCD4)
            )

            // 4. Timer at the Bottom (Smaller)
            Text(
                text = time,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp), // Lift up from bottom bezel
                textAlign = TextAlign.Center,
                fontSize = 24.sp, // Smaller font (was 40+)
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "1. Needs Sign In")
@Composable
fun NeedsSignInPreview() {
    Get_FocusedTheme {
        WearApp(
            appState = AppState.NeedsSignIn,
            onSignInClick = {},
            onEventClick = {},
            onStopClick = {},
            onTestAlarmClick = {}
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "2. Event List")
@Composable
fun EventListPreview() {
    Get_FocusedTheme {
        WearApp(
            appState = AppState.ShowEventList(
                events = listOf(
                    UiEvent("Morning Standup", System.currentTimeMillis(), 0),
                    UiEvent("Design Sync", System.currentTimeMillis() + 3600000, 0)
                )
            ),
            onSignInClick = {},
            onEventClick = {},
            onStopClick = {},
            onTestAlarmClick = {}
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "3. Waiting for Event")
@Composable
fun WaitingForEventPreview() {
    Get_FocusedTheme {
        WearApp(
            appState = AppState.WaitingForEvent(
                currentTime = "10:05 AM",
                eventTitle = "Team Lunch"
            ),
            onSignInClick = {},
            onEventClick = {},
            onStopClick = {},
            onTestAlarmClick = {}
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "4. Countdown")
@Composable
fun CountdownPreview() {
    Get_FocusedTheme {
        WearApp(
            appState = AppState.ShowCountdown(
                progress = 0.75f,
                time = "28:30",
                currentTime = "10:30 AM",
                eventTitle = "Coffee Break"
            ),
            onSignInClick = {},
            onEventClick = {},
            onStopClick = {},
            onTestAlarmClick = {}
        )
    }
}