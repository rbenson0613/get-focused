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

        // 1️⃣ Start the timer service in the background
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

        // 2️⃣ Briefly wake the screen so the user can see the notification
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "GetFocused:EventWake"
            )
            wakeLock.acquire(2000)
            wakeLock.release()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire wake lock", e)
        }

        // 3️⃣ Create the notification (full-screen or fallback)
        showEventNotification(context, eventTitle, duration)
    }

    private fun showEventNotification(context: Context, eventTitle: String, duration: Long) {
        createNotificationChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("auto_start_event", true)
            putExtra("eventTitle", eventTitle)
            putExtra("duration", duration)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = context.getSystemService(NotificationManager::class.java)
        val canUseFullScreen =
            if (Build.VERSION.SDK_INT >= 34) {  // Android 14 (API 34)
                nm.canUseFullScreenIntent()
            } else {
                true
            }

        Log.d(TAG, "canUseFullScreenIntent = $canUseFullScreen")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Event starting now")
            .setContentText(eventTitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)

        if (canUseFullScreen) {
            // Request a full-screen UI when allowed
            builder.setFullScreenIntent(pendingIntent, true)
            Log.d(TAG, "Using full-screen intent for event '$eventTitle'")
        } else {
            Log.w(TAG, "Full-screen intent not allowed; using regular high-priority notification")
        }

        val notification = builder.build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Event Start Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications shown when an event starts"
                setShowBadge(false)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
