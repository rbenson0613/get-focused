package com.example.get_focused.presentation

import android.os.Bundle
import android.widget.TextClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.items
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.get_focused.R
import com.example.get_focused.WearEventManager
import com.example.get_focused.presentation.theme.Get_FocusedTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WearApp("Android")
        }
    }
}

@Composable
fun WearApp(greetingName: String) {
    Get_FocusedTheme {
        val events by WearEventManager.events.collectAsState()
        val locks by WearEventManager.locks.collectAsState()

        // State to track if we are currently locked
        var isLocked by remember { mutableStateOf(false) }

        // Check lock status every second
        LaunchedEffect(locks) {
            while(true) {
                val now = System.currentTimeMillis()
                // A lock is active if NOW is between start and end
                isLocked = locks.any { now >= it.startTime && now <= it.endTime }
                delay(1000L)
            }
        }

        if (isLocked) {
            // LOCKED MODE: Simple Time Only
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black), // Black background saves battery
                contentAlignment = Alignment.Center
            ) {
                // Using AndroidView to render a native TextClock 
                // This automatically handles 12/24h format user settings
                AndroidView(
                    factory = { context ->
                        TextClock(context).apply {
                            textSize = 40f
                            setTextColor(android.graphics.Color.WHITE)
                            // "h:mm a" for 12h, "H:mm" for 24h
                            // TextClock handles system prefs automatically if we don't force it
                        }
                    }
                )

                Text(
                    text = "Focus Mode",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                    style = MaterialTheme.typography.caption1
                )
            }
        } else {
            // NORMAL MODE: Standard Dashboard
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                if (events.isEmpty()) {
                    TimeText()
                    Greeting(greetingName = greetingName)
                } else {
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            // Header spacer could go here
                        }
                        items(events) { eventTitle ->
                            Text(
                                text = eventTitle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    TimeText()
                }
            }
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}