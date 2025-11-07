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
        val eventTitle = intent.getStringExtra("eventTitle") ?: "Event"
        val duration = intent.getLongExtra("duration", 0L)

        Log.d(TAG, "onReceive: Event '$eventTitle' triggered, duration=$duration")

        // 1️⃣ Start the timer service
        startTimerService(context, eventTitle, duration)

        // 2️⃣ Wake the screen and show full-screen notification
        wakeScreenAndNotify(context, eventTitle, duration)
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

    private fun wakeScreenAndNotify(context: Context, eventTitle: String, duration: Long) {
        // Wake the screen with full wake lock
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "GetFocused:EventWake"
            )
            wakeLock.acquire(5000) // Hold for 5 seconds
            Log.d(TAG, "Screen wake lock acquired")

            // Show the full-screen notification
            showFullScreenNotification(context, eventTitle, duration)

            wakeLock.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock or show notification", e)
        }
    }

    private fun showFullScreenNotification(context: Context, eventTitle: String, duration: Long) {
        createNotificationChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_FROM_BACKGROUND
            putExtra("auto_start_event", true)
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_CANCEL_CURRENT
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check if we can use full-screen intents
        val canUseFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm.canUseFullScreenIntent()
        } else {
            true
        }

        Log.d(TAG, "Can use full-screen intent: $canUseFullScreen")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎯 Event Starting Now!")
            .setContentText(eventTitle)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$eventTitle is starting now. Tap to begin timer."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
            .setContentIntent(fullScreenPendingIntent)

        // Set full-screen intent - this is what makes it pop up
        if (canUseFullScreen) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            Log.d(TAG, "Full-screen intent set")
        } else {
            Log.w(TAG, "Full-screen intent not allowed - using high-priority notification")
        }

        val notification = builder.build()
        nm.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "Full-screen notification posted for '$eventTitle'")
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Event Start Alerts",
                NotificationManager.IMPORTANCE_HIGH
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