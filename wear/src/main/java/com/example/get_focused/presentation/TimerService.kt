package com.example.get_focused.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.example.get_focused.START_TIMER"
        const val ACTION_STOP = "com.example.get_focused.STOP_TIMER"

        const val EXTRA_EVENT_TITLE = "eventTitle"
        const val EXTRA_DURATION_MS = "durationMs"

        private const val CHANNEL_ID = "TimerChannel"
        private const val NOTIFICATION_ID = 42
    }

    sealed class TimerState {
        data class Counting(val remainingTime: Long, val progress: Float, val eventTitle: String) : TimerState()
        object Finished : TimerState()
        object Idle : TimerState()
    }

    private var timer: CountDownTimer? = null
    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState = _timerState.asStateFlow()

    override fun onBind(intent: Intent?): IBinder? = TimerBinder()

    inner class TimerBinder : android.os.Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer(intent)
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    private fun startTimer(intent: Intent) {
        val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Event"
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)

        if (durationMs <= 0L) {
            Log.w("TimerService", "Invalid duration: $durationMs")
            stopSelf()
            return
        }

        createNotificationChannel()
        val notification = buildNotification(eventTitle, durationMs)
        startForeground(NOTIFICATION_ID, notification)

        timer?.cancel()
        timer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = 1f - millisUntilFinished.toFloat() / durationMs.toFloat()
                _timerState.value = TimerState.Counting(millisUntilFinished, progress, eventTitle)
                updateNotification(eventTitle, millisUntilFinished)
            }

            override fun onFinish() {
                _timerState.value = TimerState.Finished
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }.start()

        Log.d("TimerService", "Timer started for $eventTitle ($durationMs ms)")
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
        _timerState.value = TimerState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d("TimerService", "Timer stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running countdown timer"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(eventTitle: String, durationMs: Long): Notification {
        val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        val timeLeft = String.format("%02d:%02d", minutes, seconds)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Timer Active")
            .setContentText("$eventTitle • $timeLeft remaining")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(eventTitle: String, millisLeft: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val updated = buildNotification(eventTitle, millisLeft)
        nm.notify(NOTIFICATION_ID, updated)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
        Log.d("TimerService", "Destroyed")
    }
}
