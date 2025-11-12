package com.example.get_focused.presentation

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import android.os.PowerManager
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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

import androidx.lifecycle.lifecycleScope
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import com.example.get_focused.presentation.notificationDataStore
import androidx.lifecycle.lifecycleScope
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import com.example.get_focused.presentation.notificationDataStore


class FullScreenCountdownActivity : ComponentActivity() {

    companion object {
        private const val TAG = "FullScreenCountdown"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "=== onCreate START ===")

        super.onCreate(savedInstanceState)

        // Check if we should mark as opened
        if (intent.getBooleanExtra("mark_as_opened", false)) {
            lifecycleScope.launch {
                applicationContext.notificationDataStore.edit { prefs ->
                    prefs[booleanPreferencesKey("event_opened")] = true
                }
                Log.d("FullScreenCountdown", "Marked event as opened")
            }
        }

        Log.d(TAG, "=== onCreate after super ===")

        try {
            // MUST set these flags before setContent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                Log.d(TAG, "Set show when locked flags (API 27+)")
            }

            // Keep screen on and show over lockscreen
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )

            Log.d(TAG, "Window flags set")

            // Acquire wake lock to ensure screen stays on
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "GetFocused:FullScreenWake"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max

            Log.d(TAG, "Wake lock acquired")

            val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
            val durationMs = intent.getLongExtra("duration", 60000L)

            Log.d(TAG, "Event: $eventTitle, Duration: $durationMs ms")

            Log.d(TAG, "About to setContent...")

            setContent {
                Get_FocusedTheme {
                    FullScreenCountdown(
                        eventTitle = eventTitle,
                        durationMs = durationMs,
                        onDismiss = {
                            Log.d(TAG, "Dismiss clicked")
                            finish()
                        },
                        onOpenApp = {
                            Log.d(TAG, "Open App clicked")
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

            Log.d(TAG, "=== onCreate COMPLETE ===")

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
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
    var timeRemainingMs by remember { mutableLongStateOf(durationMs) }
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
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
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            // Center content
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = eventTitle,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Icon(
                    imageVector = Icons.Default.Coffee,
                    contentDescription = "Event icon",
                    modifier = Modifier.size(28.dp),
                    tint = Color(0xFF00BCD4)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Button(
                    onClick = onOpenApp,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text("Open", fontSize = 12.sp)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text("Dismiss", fontSize = 12.sp)
                }
            }

            // Countdown timer at bottom
            Text(
                text = timeString,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 28.sp,
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