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
        private const val REPEAT_INTERVAL_MS = 10_000L

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

        // CHANGED: Immediately launch LockTaskActivity instead of just showing notification
        Log.d(TAG, "Launching LockTaskActivity explicitly for strict enforcement")
        val lockIntent = Intent(context, LockTaskActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LockTaskActivity.EXTRA_TITLE, eventTitle)
            putExtra(LockTaskActivity.EXTRA_UNLOCK_TIME, System.currentTimeMillis() + duration)
        }
        context.startActivity(lockIntent)

        // We can still show the notification as a fallback/informational,
        // but the activity launch above takes priority.
        showNotification(context, eventTitle, duration, isRepeat = false)

        // Optional: Schedule repeat if you want to ensure they didn't somehow escape
        scheduleRepeatNotification(context, eventTitle, duration)
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
        // In strict mode, dismissing shouldn't really be possible or allowed to stop the lock
        // But we keep the logic for completeness
        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val duration = intent.getLongExtra("duration", 0L)
        scheduleRepeatNotification(context, eventTitle, duration)
    }

    private fun handleRepeat(context: Context, intent: Intent) {
        Log.d(TAG, "Repeat notification triggered")
        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val duration = intent.getLongExtra("duration", 0L)

        // In strict mode, repeat logic simply enforces the lock screen again
        Log.d(TAG, "Re-enforcing lock screen")
        val lockIntent = Intent(context, LockTaskActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LockTaskActivity.EXTRA_TITLE, eventTitle)
            // Note: You might want to calculate remaining time here instead of full duration
            putExtra(LockTaskActivity.EXTRA_UNLOCK_TIME, System.currentTimeMillis() + duration)
        }
        context.startActivity(lockIntent)
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TimerService", e)
        }
    }

    private fun showNotification(context: Context, eventTitle: String, duration: Long, isRepeat: Boolean) {
        createNotificationChannel(context)

        // Full-screen intent to launch countdown directly (like an alarm)
        val fullScreenIntent = Intent(context, LockTaskActivity::class.java).apply { // Changed to LockTaskActivity
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(LockTaskActivity.EXTRA_TITLE, eventTitle)
            putExtra(LockTaskActivity.EXTRA_UNLOCK_TIME, System.currentTimeMillis() + duration)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎯 $eventTitle")
            .setContentText("Focus time started")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, builder.build())
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
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}