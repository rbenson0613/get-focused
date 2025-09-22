package com.example.get_focused.presentation

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.get_focused.CountdownService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CountdownViewModel(application: Application) : AndroidViewModel(application) {

    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime: StateFlow<Long> = _remainingTime

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private var countdownService: CountdownService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CountdownService.LocalBinder
            countdownService = binder.getService()
            isBound = true
            viewModelScope.launch {
                countdownService?.remainingTime?.collect { _remainingTime.value = it }
            }
            viewModelScope.launch {
                countdownService?.isRunning?.collect { _isRunning.value = it }
            }
            viewModelScope.launch {
                countdownService?.isPaused?.collect { _isPaused.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            countdownService = null
            isBound = false
        }
    }

    init {
        Intent(application, CountdownService::class.java).also { intent ->
            application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun startCountdown(duration: Long) {
        if (isBound) {
            countdownService?.startTimer(duration)
        }
    }

    fun pauseCountdown() {
        if (isBound) {
            countdownService?.pauseTimer()
        }
    }

    fun resumeCountdown() {
        if (isBound) {
            countdownService?.resumeTimer()
        }
    }

    fun resetCountdown() {
        if (isBound) {
            countdownService?.stopTimer()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            getApplication<Application>().unbindService(connection)
            isBound = false
        }
    }
}
