package com.example.get_focused.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.get_focused.presentation.theme.Get_FocusedTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.WindowManager
import android.os.PowerManager
import android.content.Context
import android.util.Log

class FullScreenCountdownActivity : ComponentActivity() {

    private val TAG = "FullScreenCountdown"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate called")

        // Keep screen on and show over lockscreen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // Acquire wake lock to ensure screen stays on
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "GetFocused:FullScreenWake"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max

        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val durationMs = intent.getLongExtra("duration", 60000L)

        Log.d(TAG, "Event: $eventTitle, Duration: $durationMs ms")

        setContent {
            Get_FocusedTheme {
                FullScreenCountdown(
                    eventTitle = eventTitle,
                    durationMs = durationMs,
                    onDismiss = { finish() },
                    onOpenApp = {
                        // Open main activity and pass the event data
                        val mainIntent = android.content.Intent(this, MainActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("auto_start_event", true)
                            putExtra("eventTitle", eventTitle)
                            putExtra("duration", durationMs)
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        Log.d(TAG, "onDestroy - wake lock released")
    }
}

@Composable
fun FullScreenCountdown(
    eventTitle: String,
    durationMs: Long,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit
) {
    var timeRemainingMs by remember { mutableStateOf(durationMs) }
    var currentTimeStr by remember { mutableStateOf("") }

    // Update countdown every second
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs

        while (timeRemainingMs > 0) {
            val now = System.currentTimeMillis()
            timeRemainingMs = (endTime - now).coerceAtLeast(0)

            // Update current time
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            currentTimeStr = timeFormat.format(Date(now))

            delay(1000) // Update every second
        }
    }

    val progress = if (durationMs > 0) {
        (timeRemainingMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val timeString = formatTime(timeRemainingMs)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background progress indicator
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            indicatorColor = Color(0xFF00BCD4),
            trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Current time at top
            Text(
                text = currentTimeStr,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
            )

            // Center content
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = eventTitle,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Icon(
                    imageVector = Icons.Default.Coffee,
                    contentDescription = "Event icon",
                    modifier = Modifier.size(32.dp),
                    tint = Color(0xFF00BCD4)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Button(
                    onClick = onOpenApp,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("Open App")
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("Dismiss")
                }
            }

            // Countdown timer at bottom
            Text(
                text = timeString,
                modifier = Modifier.align(Alignment.BottomCenter),
                textAlign = TextAlign.Center,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00BCD4)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        minutes > 0 -> String.format("%d:%02d", minutes, seconds)
        else -> String.format("0:%02d", seconds)
    }
}