package com.example.get_focused.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.items
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable


@Serializable
data class UiEvent(
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long
)

@Composable
fun SignInScreen(onSignInClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Please sign in to sync your Google Calendar events.",
            textAlign = TextAlign.Center
        )
        Button(onClick = onSignInClick, modifier = Modifier.padding(top = 16.dp)) {
            Text("Sign In")
        }
    }
}

// A new Composable screen
@Composable
fun PermissionScreen(
    onGrantClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("We need notifications", textAlign = TextAlign.Center)
        Text(
            "To alert you when an event is about to start, please grant the notification permission.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption2
        )
        Button(onClick = onGrantClick, modifier = Modifier.padding(top = 8.dp)) {
            Text("Grant")
        }
    }
}

@Composable
fun EventListScreen(
    events: List<UiEvent>,
    onEventClick: (UiEvent) -> Unit,
    onTestAlarmClick: (() -> Unit)? = null
) {
    if (events.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No upcoming events found.")
        }
    } else {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ListHeader {
                    Text("Upcoming Events")
                }
            }

            onTestAlarmClick?.let { testClick ->
                item {
                    Chip(
                        onClick = testClick,
                        label = { Text("🔔 Test Alarm (10s)") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            items(events) { event ->
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val time = timeFormat.format(Date(event.startTimeMillis))
                Chip(
                    onClick = { onEventClick(event) },
                    label = { Text(event.title) },
                    secondaryLabel = { Text(time) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}