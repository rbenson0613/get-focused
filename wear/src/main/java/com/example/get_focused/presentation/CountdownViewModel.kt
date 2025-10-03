package com.example.get_focused.presentation

import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class CountdownViewModel(application: Application) : AndroidViewModel(application) {
    // For the countdown timer
    private val initialCountdownMillis = (28 * 60 + 30) * 1000L // 28 minutes 30 seconds
    private val _time = MutableStateFlow("28:30")
    val time = _time.asStateFlow()

    private val _progress = MutableStateFlow(1f)
    val progress = _progress.asStateFlow()

    // For the current time clock
    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val _currentTime = MutableStateFlow(timeFormatter.format(Date()))
    val currentTime = _currentTime.asStateFlow()

    private val countdownTimer = object : CountDownTimer(initialCountdownMillis, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
            _time.value = String.format("%02d:%02d", minutes, seconds)
            _progress.value = millisUntilFinished.toFloat() / initialCountdownMillis
        }

        override fun onFinish() {
            _time.value = "00:00"
            _progress.value = 0f
            triggerNotification()
        }
    }

    private fun triggerNotification() {
        val context = getApplication<Application>().applicationContext

        // Vibrate
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            // Deprecated in API 26
            vibrator.vibrate(500)
        }

        // Play sound
        try {
            val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationSoundUri)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var clockTimer: Timer? = null

    init {
        startClock()
        countdownTimer.start()
    }

    private fun startClock() {
        clockTimer = Timer()
        clockTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                _currentTime.value = timeFormatter.format(Date())
            }
        }, 0, 1000)
    }

    override fun onCleared() {
        super.onCleared()
        countdownTimer.cancel()
        clockTimer?.cancel()
    }
}