package com.example.get_focused.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Temporary foreground service that launches activities from background.
 * Foreground services are allowed to start activities even when app is in background.
 */
class ActivityLauncherService : Service() {

    companion object {
        private const val TAG = "ActivityLauncher"
        private const val CHANNEL_ID = "ActivityLauncherChannel"
        private const val NOTIFICATION_ID = 9999

        const val EXTRA_TARGET_ACTIVITY = "target_activity"
        const val EXTRA_EVENT_TITLE = "event_title"
        const val EXTRA_DURATION = "duration"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")

        // MUST start foreground immediately (Android 8.0+)
        startForeground(NOTIFICATION_ID, createNotification())

        // Launch the target activity
        val targetActivity = intent?.getStringExtra(EXTRA_TARGET_ACTIVITY)
        val eventTitle = intent?.getStringExtra(EXTRA_EVENT_TITLE)
        val duration = intent?.getLongExtra(EXTRA_DURATION, 0L)

        if (targetActivity == null) {
            Log.e(TAG, "No target activity specified")
            stopSelfAndCleanup()
            return START_NOT_STICKY
        }

        try {
            val activityIntent = Intent().apply {
                setClassName(packageName, targetActivity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY)  // Add NO_HISTORY
                putExtra("eventTitle", eventTitle)
                putExtra("duration", duration)
                putExtra("mark_as_opened", true)
            }

            startActivity(activityIntent)
            Log.d(TAG, "✅ Successfully launched activity: $targetActivity")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to launch activity", e)
        }

        // Stop service immediately after launching activity
        stopSelfAndCleanup()

        return START_NOT_STICKY
    }

    private fun stopSelfAndCleanup() {
        Log.d(TAG, "Stopping service")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Starting alarm...")
            .setContentText("Launching countdown")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(false)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Activity Launcher",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Temporary notification for launching activities"
                setShowBadge(false)
            }

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}