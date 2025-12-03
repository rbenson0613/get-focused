package com.example.get_focused.presentation

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.get_focused.admin.SetupModeHandler
import com.example.get_focused.admin.SetupScreen
import com.example.get_focused.data.eventsDataStore
import com.example.get_focused.presentation.theme.Get_FocusedTheme
import com.example.get_focused.presentation.ui.EventListScreen
import com.example.get_focused.presentation.ui.SignInScreen
import com.example.get_focused.presentation.ui.UiEvent
import com.example.get_focused.sync.DataSyncService
import com.example.get_focused.sync.SyncedEvent
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

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

    private val eventStartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == EventStartReceiver.ACTION_EVENT_STARTED) {
                val eventTitle = intent.getStringExtra("eventTitle") ?: return
                Log.d(TAG, "Event started broadcast received: $eventTitle")

                lifecycleScope.launch {
                    applicationContext.notificationDataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("event_opened")] = true
                    }
                }
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

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(syncReceiver, IntentFilter(DataSyncService.ACTION_SYNC_EVENTS))

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(eventStartReceiver, IntentFilter(EventStartReceiver.ACTION_EVENT_STARTED))

        setContent {
            val context = LocalContext.current
            val setupModeHandler = remember { SetupModeHandler(context) }
            var isSetupComplete by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                isSetupComplete = setupModeHandler.isSetupComplete()
            }

            if (isSetupComplete == false) {
                SetupScreen(
                    onSetupComplete = {
                        lifecycleScope.launch {
                            setupModeHandler.completeSetup()
                            isSetupComplete = true
                        }
                    }
                )
            } else if (isSetupComplete == true) {
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
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoStartIntent(intent)
    }

    private fun handleAutoStartIntent(intent: Intent?) {
        val isAutoStart = intent?.getBooleanExtra("auto_start_event", false) ?: false
        val markAsOpened = intent?.getBooleanExtra("mark_as_opened", false) ?: false

        if (markAsOpened) {
            lifecycleScope.launch {
                applicationContext.notificationDataStore.edit { prefs ->
                    prefs[booleanPreferencesKey("event_opened")] = true
                }
                Log.d(TAG, "Marked as opened from notification tap")
            }
        }

        if (isAutoStart) {
            Log.d(TAG, "Auto-start event detected")
            viewModel.forceCheckTimerState()
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        handleAutoStartIntent(intent)
        checkPendingDataItems()
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/sync-events") {
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    handleSyncDataMap(dataMapItem.dataMap)
                }
            }
        } finally {
            dataEvents.release()
        }
    }

    private fun handleSyncDataMap(dataMap: DataMap) {
        val eventsJson = dataMap.getString("events_json")
        if (eventsJson != null) {
            try {
                val syncedEvents = Json.decodeFromString<List<SyncedEvent>>(eventsJson)
                val uiEvents = syncedEvents.mapNotNull { event ->
                    event.endTime?.let { endTime ->
                        if ((endTime - event.startTime) <= TimeUnit.HOURS.toMillis(24)) {
                            UiEvent(event.title, event.startTime, endTime)
                        } else null
                    }
                }

                val now = System.currentTimeMillis()
                val activeEvent = uiEvents.firstOrNull { now >= it.startTimeMillis && now < it.endTimeMillis }

                if (activeEvent != null) {
                    viewModel.startCountdownForEvent(activeEvent)
                } else {
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
                            handleSyncDataMap(DataMapItem.fromDataItem(item).dataMap)
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
        is AppState.NeedsSignIn -> SignInScreen(onSignInClick = onSignInClick)
        is AppState.ShowEventList -> EventListScreen(appState.events, onEventClick, onTestAlarmClick)
        is AppState.WaitingForEvent -> CountdownScreen(1f, "Waiting...", appState.currentTime, appState.eventTitle, onStopClick)
        is AppState.ShowCountdown -> CountdownScreen(appState.progress, appState.time, appState.currentTime, appState.eventTitle, onStopClick)
    }
}

@Composable
fun CountdownScreen(
    progress: Float,
    time: String,
    currentTime: String,
    eventTitle: String,
    onStopClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            indicatorColor = Color(0xFF00BCD4),
            trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f)
        )
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = eventTitle,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 30.dp, start = 10.dp, end = 10.dp),
                textAlign = TextAlign.Center,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Icon(
                imageVector = Icons.Default.Coffee,
                contentDescription = "Event Icon",
                modifier = Modifier.align(Alignment.Center).size(56.dp),
                tint = Color(0xFF00BCD4)
            )
            Text(
                text = time,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
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