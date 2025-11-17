package com.example.get_focused.presentation

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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

val Context.notificationDataStore by preferencesDataStore(name = "notification_prefs")

class EventStartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EventStartReceiver"
        private const val CHANNEL_ID = "EventStartChannel"
        private const val NOTIFICATION_ID = 1002
        private const val REPEAT_INTERVAL_MS = 10_000L // Back to 60 seconds

        const val ACTION_DISMISS = "com.example.get_focused.NOTIFICATION_DISMISSED"
        const val ACTION_REPEAT = "com.example.get_focused.REPEAT_NOTIFICATION"
        const val ACTION_EVENT_STARTED = "com.example.get_focused.EVENT_STARTED"

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

        // Check if MainActivity is already running
        val isAppInForeground = isAppInForeground(context)
        Log.d(TAG, "Is app in foreground: $isAppInForeground")

        if (isAppInForeground) {
            // App is open - just broadcast to trigger countdown in existing activity
            Log.d(TAG, "App already open - broadcasting event start")

            val broadcastIntent = Intent(ACTION_EVENT_STARTED).apply {
                putExtra("eventTitle", eventTitle)
                putExtra("duration", duration)
            }
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(context)
                .sendBroadcast(broadcastIntent)

            // Mark as opened since app is already open
            CoroutineScope(Dispatchers.IO).launch {
                context.notificationDataStore.edit { prefs ->
                    prefs[EVENT_OPENED_KEY] = true
                }
            }
        } else {
            // App is closed - show full-screen notification (this should auto-launch)
            Log.d(TAG, "App closed - showing full-screen notification")
            showNotification(context, eventTitle, duration, isRepeat = false)
            scheduleRepeatNotification(context, eventTitle, duration)
        }
    }

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = context.packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        Log.d(TAG, "Notification dismissed by user")
        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val duration = intent.getLongExtra("duration", 0L)

        CoroutineScope(Dispatchers.IO).launch {
            val hasOpened = context.notificationDataStore.data
                .map { prefs -> prefs[EVENT_OPENED_KEY] ?: false }
                .first()

            if (!hasOpened) {
                Log.d(TAG, "User dismissed without opening - will repeat notification")
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
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        val repeatIntent = Intent(context, EventStartReceiver::class.java).apply {
            action = ACTION_REPEAT
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + 1,
            repeatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + REPEAT_INTERVAL_MS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
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

    private fun launchActivityViaAlarm(context: Context, eventTitle: String, duration: Long) {
        val activityIntent = Intent(context, FullScreenCountdownActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
            putExtra("mark_as_opened", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create a "show intent" that will open when user taps the alarm clock indicator
        // This MUST be different from the alarm intent itself
        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val showPendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() + 1).toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        // The key: Use the showIntent (not the main pendingIntent) in AlarmClockInfo
        val alarmClockInfo = android.app.AlarmManager.AlarmClockInfo(
            System.currentTimeMillis() + 100, // 100ms delay
            showPendingIntent  // ← This is what appears in system UI
        )

        // But set the actual alarm to fire the activity intent
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        Log.d(TAG, "✅ Activity launch scheduled via AlarmClock")
    }

    private fun launchActivityViaService(context: Context, eventTitle: String, duration: Long) {
        val serviceIntent = Intent(context, ActivityLauncherService::class.java).apply {
            putExtra(ActivityLauncherService.EXTRA_TARGET_ACTIVITY,
                "com.example.get_focused.presentation.FullScreenCountdownActivity")
            putExtra(ActivityLauncherService.EXTRA_EVENT_TITLE, eventTitle)
            putExtra(ActivityLauncherService.EXTRA_DURATION, duration)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "✅ Started ActivityLauncherService")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start ActivityLauncherService", e)
        }
    }

    private fun showNotification(context: Context, eventTitle: String, duration: Long, isRepeat: Boolean) {
        createNotificationChannel(context)

        // Full-screen intent to launch countdown directly (like an alarm)
        val fullScreenIntent = Intent(context, FullScreenCountdownActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
            putExtra("mark_as_opened", true)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Fallback to MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("auto_start_event", true)
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
            putExtra("mark_as_opened", true)
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt() + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, EventStartReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
        }

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt() + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Open on watch",
            openPendingIntent
        ).build()

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
            .setAutoCancel(false)
            .setOngoing(false)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)  // ← KEY LINE for alarm-like behavior
            .addAction(openAction)
            .addAction(clearAction)
            .setDeleteIntent(dismissPendingIntent)

        val notification = builder.build()
        nm.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "Full-screen notification posted for '$eventTitle' (repeat=$isRepeat)")
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