package com.example.get_focused.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class TimerService : Service() {

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var countdownJob: Job? = null
    private var ongoingActivity: OngoingActivity? = null

    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState = _timerState.asStateFlow()

    companion object {
        const val ACTION_START = "com.example.get_focused.presentation.START"
        const val ACTION_STOP = "com.example.get_focused.presentation.STOP"
        const val EXTRA_DURATION_MS = "com.example.get_focused.presentation.EXTRA_DURATION_MS"
        const val EXTRA_EVENT_TITLE = "com.example.get_focused.presentation.EXTRA_EVENT_TITLE"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "TimerServiceChannel"
    }

    sealed class TimerState {
        object Idle : TimerState()
        data class Counting(val remainingTime: Long, val progress: Float, val eventTitle: String) : TimerState()
        object Finished : TimerState()
    }

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getLongExtra(EXTRA_DURATION_MS, 0)
                val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: ""
                if (duration > 0) {
                    startForegroundService()
                    startCountdown(duration, eventTitle)
                }
            }
            ACTION_STOP -> {
                stopCountdown()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startCountdown(durationMs: Long, eventTitle: String) {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            val totalDuration = durationMs
            var remainingTime = totalDuration
            while (remainingTime >= 0) {
                val elapsedTime = totalDuration - remainingTime
                val progress = elapsedTime.toFloat() / totalDuration
                _timerState.value = TimerState.Counting(remainingTime, progress, eventTitle)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60
                val timeString = String.format("%02d:%02d", minutes, seconds)
                ongoingActivity?.update(applicationContext, Status.Builder().addTemplate(timeString).build())
                delay(1000)
                remainingTime -= 1000
            }
            _timerState.value = TimerState.Finished
            stopForeground(true)
            stopSelf()
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        _timerState.value = TimerState.Idle
        stopForeground(true)
        ongoingActivity = null
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Get Focused")
            .setContentText("Countdown running...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        ongoingActivity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notificationBuilder)
            .setAnimatedIcon(android.R.drawable.ic_dialog_info)
            .setTouchIntent(
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                }
            )
            .build()
        ongoingActivity?.apply(applicationContext)
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Timer Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Get Focused")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
