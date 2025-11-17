package com.example.get_focused.presentation

import android.os.Bundle
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
import androidx.compose.material.icons.filled.Lock
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.get_focused.admin.DeviceOwnerManager
import com.example.get_focused.presentation.theme.Get_FocusedTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockTaskActivity : ComponentActivity() {

    private lateinit var deviceOwnerManager: DeviceOwnerManager
    private var unlockTimeMillis: Long = 0L

    companion object {
        private const val TAG = "LockTaskActivity"
        const val EXTRA_UNLOCK_TIME = "unlock_time"
        const val EXTRA_TITLE = "title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceOwnerManager = DeviceOwnerManager(this)

        unlockTimeMillis = intent.getLongExtra(EXTRA_UNLOCK_TIME, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Focus Time"

        Log.d(TAG, "Lock task activity started, unlock at: $unlockTimeMillis")

        // Start lock task mode if device owner
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.startLockTask(this)
            deviceOwnerManager.setStatusBarDisabled(true)
        } else {
            Log.e(TAG, "Not device owner - cannot start lock task")
        }

        setContent {
            Get_FocusedTheme {
                LockTaskScreen(
                    title = title,
                    unlockTimeMillis = unlockTimeMillis,
                    onUnlockTimeReached = {
                        exitLockTask()
                    }
                )
            }
        }
    }

    private fun exitLockTask() {
        Log.d(TAG, "Unlock time reached, exiting lock task")

        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.setStatusBarDisabled(false)
            deviceOwnerManager.stopLockTask(this)
        }

        finish()
    }

    override fun onBackPressed() {
        // Prevent back button in lock task mode
        if (deviceOwnerManager.isLockTaskModeActive()) {
            Log.d(TAG, "Back pressed blocked - in lock task mode")
            return
        }
        super.onBackPressed()
    }
}

@Composable
fun LockTaskScreen(
    title: String,
    unlockTimeMillis: Long,
    onUnlockTimeReached: () -> Unit
) {
    var timeRemainingMs by remember { mutableLongStateOf(0L) }
    var currentTimeStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            timeRemainingMs = (unlockTimeMillis - now).coerceAtLeast(0)

            if (timeRemainingMs <= 0) {
                onUnlockTimeReached()
                break
            }

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            currentTimeStr = timeFormat.format(Date(now))

            delay(1000)
        }
    }

    val progress = if (unlockTimeMillis > 0) {
        val totalDuration = unlockTimeMillis - (unlockTimeMillis - timeRemainingMs)
        (timeRemainingMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val unlockTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault())
        .format(Date(unlockTimeMillis))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        // Background progress indicator
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            indicatorColor = Color(0xFFFF6B35),
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
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFFFF6B35)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Locked until $unlockTimeStr",
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // Countdown timer at bottom
            Text(
                text = formatTime(timeRemainingMs),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B35)
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