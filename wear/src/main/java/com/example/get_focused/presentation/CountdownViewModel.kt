package com.example.get_focused.presentation

import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.get_focused.calendar.CalendarManager
import com.example.get_focused.presentation.ui.UiEvent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

sealed class AppState {
    object Loading : AppState()
    object NeedsSignIn : AppState()
    data class ShowEventList(val events: List<UiEvent>) : AppState()
    data class WaitingForEvent(val currentTime: String, val eventTitle: String) : AppState()
    data class ShowCountdown(
        val progress: Float,
        val time: String,
        val currentTime: String,
        val eventTitle: String
    ) : AppState()
}

class CountdownViewModel(application: Application) : AndroidViewModel(application) {

    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState = _appState.asStateFlow()

    private var countdownTimer: CountDownTimer? = null
    private var eventStartTimer: Timer? = null
    private var clockTimer: Timer? = null
    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val _currentTime = MutableStateFlow(timeFormatter.format(Date()))

    init {
        startClock()
        checkSignInStatus()
    }

    fun checkSignInStatus() {
        viewModelScope.launch {
            _appState.value = AppState.Loading
            val account = GoogleSignIn.getLastSignedInAccount(getApplication())
            if (account != null && GoogleSignIn.hasPermissions(account, Scope("https://www.googleapis.com/auth/calendar.readonly"))) {
                fetchCalendarEvents(account)
            } else {
                _appState.value = AppState.NeedsSignIn
            }
        }
    }

    private fun fetchCalendarEvents(account: GoogleSignInAccount) {
        viewModelScope.launch {
            try {
                val accessToken = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        getApplication(),
                        account.account!!,
                        "oauth2:${Scope("https://www.googleapis.com/auth/calendar.readonly").scopeUri}"
                    )
                }
                val events = CalendarManager.getUpcomingEvents(accessToken)

                val rfc3339Formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

                val uiEvents = events.mapNotNull { event ->
                    try {
                        val start = event.start?.dateTime?.let { rfc3339Formatter.parse(it)?.time }
                        val end = event.end?.dateTime?.let { rfc3339Formatter.parse(it)?.time }

                        if (start != null && end != null) {
                            UiEvent(
                                title = event.summary ?: "No Title",
                                startTimeMillis = start,
                                endTimeMillis = end
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("CountdownViewModel", "Failed to parse date-time for event: ${event.summary}", e)
                        null
                    }
                }

                val now = System.currentTimeMillis()
                val activeEvent = uiEvents.firstOrNull { now >= it.startTimeMillis && now < it.endTimeMillis }

                if (activeEvent != null) {
                    val remainingDuration = activeEvent.endTimeMillis - now
                    startCountdown(remainingDuration, activeEvent.title)
                } else {
                    _appState.value = AppState.ShowEventList(uiEvents)
                }
            } catch (e: Exception) {
                Log.e("CountdownViewModel", "Error fetching calendar events or token", e)
                _appState.value = AppState.NeedsSignIn
            }
        }
    }

    fun startCountdownForEvent(event: UiEvent) {
        countdownTimer?.cancel()
        eventStartTimer?.cancel()

        val now = System.currentTimeMillis()
        val durationMillis = event.endTimeMillis - event.startTimeMillis

        if (durationMillis <= 0) {
            checkSignInStatus()
            return
        }

        if (now < event.startTimeMillis) {
            _appState.value = AppState.WaitingForEvent(_currentTime.value, event.title)

            eventStartTimer = Timer()
            eventStartTimer?.schedule(object : TimerTask() {
                override fun run() {
                    startCountdown(durationMillis, event.title)
                }
            }, event.startTimeMillis - now)

        } else {
            val remainingDuration = event.endTimeMillis - now
            if (remainingDuration > 0) {
                startCountdown(remainingDuration, event.title)
            } else {
                checkSignInStatus()
            }
        }
    }

    private fun startCountdown(durationMillis: Long, eventTitle: String) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                val timeString = String.format("%02d:%02d", minutes, seconds)
                val progress = millisUntilFinished.toFloat() / durationMillis

                _appState.value = AppState.ShowCountdown(
                    progress = progress,
                    time = timeString,
                    currentTime = _currentTime.value,
                    eventTitle = eventTitle
                )
            }

            override fun onFinish() {
                triggerNotification()
                checkSignInStatus()
            }
        }.start()
    }

    private fun triggerNotification() {
        val context = getApplication<Application>().applicationContext
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500)
        }
        try {
            val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationSoundUri)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        countdownTimer?.cancel()
        eventStartTimer?.cancel()
        clockTimer?.cancel()
    }
}