package com.example.get_focused.presentation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Create DataStore for tracking if user opened the app
val Context.notificationDataStore by preferencesDataStore(name = "notification_prefs")

class EventStartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EventStartReceiver"
        private const val CHANNEL_ID = "EventStartChannel"
        private const val NOTIFICATION_ID = 1002
        private const val REPEAT_INTERVAL_MS = 3_000L // Repeat every 60 seconds

        const val ACTION_DISMISS = "com.example.get_focused.NOTIFICATION_DISMISSED"
        const val ACTION_REPEAT = "com.example.get_focused.REPEAT_NOTIFICATION"

        private val EVENT_OPENED_KEY = booleanPreferencesKey("event_opened")
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> handleDismiss(context, intent)
            ACTION_REPEAT -> handleRepeat(context, intent)
            else -> handleEventStart(context, intent)
        }
    }

    private fun handleEventStart(context: Context, intent: Intent) {
        Log.d(TAG, "=== EventStartReceiver triggered ===")
        Log.d(TAG, "Intent action: ${intent.action}")

        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val duration = intent.getLongExtra("duration", 0L)

        Log.d(TAG, "Event: $eventTitle, Duration: $duration")

        // Reset the "opened" flag for this new event
        CoroutineScope(Dispatchers.IO).launch {
            context.notificationDataStore.edit { prefs ->
                prefs[EVENT_OPENED_KEY] = false
            }
        }

        // Start the timer service
        startTimerService(context, eventTitle, duration)

        // Launch full-screen activity
        launchFullScreenCountdown(context, eventTitle, duration)

        // Show notification with repeat logic
        showNotification(context, eventTitle, duration, isRepeat = false)

        // Schedule first repeat
        scheduleRepeatNotification(context, eventTitle, duration)
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        Log.d(TAG, "Notification dismissed by user")
        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val duration = intent.getLongExtra("duration", 0L)

        // Check if user has opened the app
        CoroutineScope(Dispatchers.IO).launch {
            val hasOpened = context.notificationDataStore.data
                .map { prefs -> prefs[EVENT_OPENED_KEY] ?: false }
                .first()

            if (!hasOpened) {
                Log.d(TAG, "User dismissed without opening - will repeat notification")
                // Schedule another notification
                scheduleRepeatNotification(context, eventTitle, duration)
            } else {
                Log.d(TAG, "User has opened app - not repeating notification")
            }
        }
    }

    private fun handleRepeat(context: Context, intent: Intent) {
        Log.d(TAG, "Repeat notification triggered")
        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val duration = intent.getLongExtra("duration", 0L)

        // Check if user has opened the app
        CoroutineScope(Dispatchers.IO).launch {
            val hasOpened = context.notificationDataStore.data
                .map { prefs -> prefs[EVENT_OPENED_KEY] ?: false }
                .first()

            if (!hasOpened) {
                Log.d(TAG, "Repeating notification - user hasn't opened yet")
                showNotification(context, eventTitle, duration, isRepeat = true)
                scheduleRepeatNotification(context, eventTitle, duration)
            } else {
                Log.d(TAG, "User has opened app - stopping repeat notifications")
            }
        }
    }

    private fun scheduleRepeatNotification(context: Context, eventTitle: String, duration: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val repeatIntent = Intent(context, EventStartReceiver::class.java).apply {
            action = ACTION_REPEAT
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + 1, // Different request code
            repeatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + REPEAT_INTERVAL_MS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        Log.d(TAG, "Scheduled repeat notification in ${REPEAT_INTERVAL_MS / 1000}s")
    }

    private fun startTimerService(context: Context, eventTitle: String, duration: Long) {
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_EVENT_TITLE, eventTitle)
            putExtra(TimerService.EXTRA_DURATION_MS, duration)
            putExtra(TimerService.EXTRA_SHOW_NOTIFICATION, false)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "TimerService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TimerService", e)
        }
    }

    private fun launchFullScreenCountdown(context: Context, eventTitle: String, duration: Long) {
        val fullScreenIntent = Intent(context, FullScreenCountdownActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_FROM_BACKGROUND

            component = android.content.ComponentName(
                context.packageName,
                "com.example.get_focused.presentation.FullScreenCountdownActivity"
            )

            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
        }

        try {
            context.startActivity(fullScreenIntent)
            Log.d(TAG, "Launched FullScreenCountdownActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch activity directly", e)
        }
    }

    private fun showNotification(context: Context, eventTitle: String, duration: Long, isRepeat: Boolean) {
        createNotificationChannel(context)

        // Intent for opening the app - marks as "opened" when tapped
        val openIntent = Intent(context, FullScreenCountdownActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
            putExtra("mark_as_opened", true) // Signal to mark as opened
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action - triggers repeat logic
        val dismissIntent = Intent(context, EventStartReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
        }

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt() + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create "Open on watch" action - this appears FIRST
        val openAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Open on watch",
            openPendingIntent
        ).build()

        // Create "Clear" action - this appears SECOND
        val clearAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Clear",
            dismissPendingIntent
        ).build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentText = if (isRepeat) {
            "$eventTitle is still in progress"
        } else {
            "Event in progress"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎯 $eventTitle")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$eventTitle is in progress. Tap to view countdown."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false) // Don't auto-cancel on tap
            .setOngoing(false)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setContentIntent(openPendingIntent)
            .addAction(openAction)  // "Open on watch" appears FIRST
            .addAction(clearAction) // "Clear" appears SECOND
            .setDeleteIntent(dismissPendingIntent) // Called when swiped away

        val notification = builder.build()
        nm.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "Notification posted for '$eventTitle' (repeat=$isRepeat)")
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Event Start Alerts",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Full-screen alerts when events start"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }
}