package com.example.get_focused

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CountdownService : Service() {

    private val binder = LocalBinder()
    private var countDownTimer: CountDownTimer? = null
    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime: StateFlow<Long> = _remainingTime
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val duration = intent?.getLongExtra(EXTRA_DURATION, 0L) ?: 0L
        startTimer(duration)
        return START_STICKY
    }

    fun startTimer(duration: Long) {
        countDownTimer?.cancel()
        _duration.value = duration
        _isRunning.value = true
        _isPaused.value = false
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _remainingTime.value = millisUntilFinished
            }

            override fun onFinish() {
                stopTimer()
                sendNotification()
            }
        }.start()
    }

    fun pauseTimer() {
        countDownTimer?.cancel()
        _isRunning.value = false
        _isPaused.value = true
    }

    fun resumeTimer() {
        startTimer(_remainingTime.value)
    }

    fun stopTimer() {
        countDownTimer?.cancel()
        _remainingTime.value = 0
        _duration.value = 0
        _isRunning.value = false
        _isPaused.value = false
        stopSelf()
    }

    private fun sendNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Countdown", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Countdown Finished")
            .setContentText("Your timer is up!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(500)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    inner class LocalBinder : Binder() {
        fun getService(): CountdownService = this@CountdownService
    }

    companion object {
        const val EXTRA_DURATION = "com.example.get_focused.EXTRA_DURATION"
        const val CHANNEL_ID = "countdown_channel"
        const val NOTIFICATION_ID = 1
    }
}
