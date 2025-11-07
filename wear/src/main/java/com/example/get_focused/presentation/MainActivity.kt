package com.example.get_focused.presentation

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

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
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
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.get_focused.presentation.theme.Get_FocusedTheme
import com.example.get_focused.presentation.ui.EventListScreen
import com.example.get_focused.presentation.ui.SignInScreen
import com.example.get_focused.presentation.ui.UiEvent
import com.example.get_focused.sync.DataSyncService

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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(syncReceiver, IntentFilter(DataSyncService.ACTION_SYNC_EVENTS))

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

        // Check if launched by alarm
        handleAutoStartIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called with extras: ${intent.extras?.keySet()?.joinToString()}")
        setIntent(intent)
        handleAutoStartIntent(intent)
    }

    private fun handleAutoStartIntent(intent: Intent?) {
        val isAutoStart = intent?.getBooleanExtra("auto_start_event", false) ?: false
        Log.d(TAG, "handleAutoStartIntent: isAutoStart=$isAutoStart")

        if (isAutoStart) {
            Log.d(TAG, "Auto-start event detected - timer should be running")
            viewModel.forceCheckTimerState()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        Log.d(TAG, "DataClient listener registered")
        Wearable.getDataClient(this).addListener(this)

        // Check if we have auto-start intent when resuming
        handleAutoStartIntent(intent)

        // Also fetch any missed items from when the app wasn't active
        checkPendingDataItems()
    }

    override fun onPause() {
        Log.d(TAG, "DataClient listener unregistered")
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
    onStopClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            indicatorColor = Color(0xFF00BCD4),
            trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f)
        )

        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {

            Text(
                text = currentTime,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 18.sp
            )

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = eventTitle,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.Coffee,
                    contentDescription = "Coffee break icon",
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStopClick) {
                    Text("Stop")
                }
            }

            Text(
                text = time,
                modifier = Modifier.align(Alignment.BottomCenter),
                textAlign = TextAlign.Center,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00BCD4)
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