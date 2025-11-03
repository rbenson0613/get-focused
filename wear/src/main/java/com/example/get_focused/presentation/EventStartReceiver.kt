package com.example.get_focused.presentation

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

class EventStartReceiver : BroadcastReceiver() {

    private val TAG = "EventStartReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val duration = intent.getLongExtra("duration", 0L)

        Log.d(TAG, "onReceive: Event '$eventTitle' triggered, duration=$duration")

        // Create channel + notification
        createNotificationChannel(context)
        val notification = createNotification(context, eventTitle)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1001, notification)

        // Optional: briefly wake the screen so user notices
        wakeScreen(context)

        // Start timer service in foreground
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_EVENT_TITLE, eventTitle)
            putExtra(TimerService.EXTRA_DURATION_MS, duration)
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

        // Launch app only if the device is awake
        if (isDeviceAwake(context)) {
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("auto_start_event", true)
                    putExtra("eventTitle", eventTitle)
                    putExtra("duration", duration)
                }
                context.startActivity(launchIntent)
                Log.d(TAG, "Launched MainActivity (device awake)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch MainActivity", e)
            }
        } else {
            Log.d(TAG, "Device asleep — notification only")
        }
    }

    /** Detect if device is awake (interactive) */
    private fun isDeviceAwake(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    /** Briefly wake the screen so the user sees the notification/timer */
    private fun wakeScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "GetFocused:EventWakeLock"
            )
            wakeLock.acquire(3000) // wake for 3 seconds
            wakeLock.release()
            Log.d(TAG, "Screen briefly woken for event")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake screen", e)
        }
    }

    /** Create the notification channel if needed */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "EventStartChannel",
                "Event Start Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when events begin"
                enableVibration(true)
            }

            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel ensured")
        }
    }

    /** Build a high-priority notification for event start */
    private fun createNotification(context: Context, eventTitle: String) =
        NotificationCompat.Builder(context, "EventStartChannel")
            .setContentTitle("Event Starting")
            .setContentText("$eventTitle is starting now.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("auto_start_event", true)
                        putExtra("eventTitle", eventTitle)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
}
