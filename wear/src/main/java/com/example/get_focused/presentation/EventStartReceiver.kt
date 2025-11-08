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

    companion object {
        private const val TAG = "EventStartReceiver"
        private const val CHANNEL_ID = "EventStartChannel"
        private const val NOTIFICATION_ID = 1002
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "=== EventStartReceiver triggered ===")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "Intent extras: ${intent.extras?.keySet()}")

        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val duration = intent.getLongExtra("duration", 0L)

        Log.d(TAG, "Event: $eventTitle, Duration: $duration")

        // Check if notification manager is available
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm == null) {
            Log.e(TAG, "NotificationManager is null!")
            return
        }

        // Check notification settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.d(TAG, "Notifications enabled: ${nm.areNotificationsEnabled()}")
        }

        Log.d(TAG, "onReceive: Event '$eventTitle' triggered, duration=$duration")

        // 1️⃣ Start the timer service
        startTimerService(context, eventTitle, duration)

        // 2️⃣ Wake the screen and show full-screen activity
        launchFullScreenCountdown(context, eventTitle, duration)
    }

    private fun startTimerService(context: Context, eventTitle: String, duration: Long) {
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_EVENT_TITLE, eventTitle)
            putExtra(TimerService.EXTRA_DURATION_MS, duration)
            putExtra(TimerService.EXTRA_SHOW_NOTIFICATION, false) // Don't show timer notification
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
        // Wear OS: Launch activity directly first (more reliable than full-screen intent)
        val fullScreenIntent = Intent(context, FullScreenCountdownActivity::class.java).apply {
            // CRITICAL: Must include these flags when starting from BroadcastReceiver
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_FROM_BACKGROUND

            // Add component explicitly
            component = android.content.ComponentName(
                context.packageName,
                "com.example.get_focused.presentation.FullScreenCountdownActivity"
            )

            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
        }

        try {
            context.startActivity(fullScreenIntent)
            Log.d(TAG, "Launched FullScreenCountdownActivity directly with flags: ${fullScreenIntent.flags}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch activity directly", e)
            e.printStackTrace()
        }

        // Also create notification as backup (in case user dismisses activity)
        createNotificationChannel(context)

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎯 $eventTitle")
            .setContentText("Event in progress")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$eventTitle is in progress. Tap to view countdown."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setContentIntent(fullScreenPendingIntent)

        val notification = builder.build()
        nm.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "Backup notification posted for '$eventTitle'")
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Event Start Alerts",
                NotificationManager.IMPORTANCE_MAX // Changed to MAX for full-screen
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