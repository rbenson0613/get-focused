package com.example.get_focused.presentation

import android.os.CountDownTimer
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class CountdownViewModel : ViewModel() {
    private val _time = MutableStateFlow("00:10")
    val time = _time.asStateFlow()

    private val _progress = MutableStateFlow(1f)
    val progress = _progress.asStateFlow()

    private val timer = object : CountDownTimer(10000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)
            _time.value = String.format("%02d:%02d", seconds / 60, seconds % 60)
            _progress.value = millisUntilFinished / 10000f
        }

        override fun onFinish() {
            _time.value = "00:00"
            _progress.value = 0f
        }
    }

    init {
        timer.start()
    }

    override fun onCleared() {
        super.onCleared()
        timer.cancel()
    }
}