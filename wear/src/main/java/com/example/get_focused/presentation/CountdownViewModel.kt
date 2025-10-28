package com.example.get_focused.presentation

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
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
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


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

private val Context.dataStore by preferencesDataStore(name = "events_cache")

class CountdownViewModel(application: Application) : AndroidViewModel(application) {

    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState = _appState.asStateFlow()

    private var timerService: TimerService? = null
    private var isBound = false
    private var eventStartPendingIntent: PendingIntent? = null
    private var clockTimer: Timer? = null
    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val _currentTime = MutableStateFlow(timeFormatter.format(Date()))

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            isBound = true
            viewModelScope.launch {
                timerService?.timerState?.collect { state ->
                    handleTimerState(state)
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    init {
        startClock()
        checkSignInStatus()
        Intent(application, TimerService::class.java).also { intent ->
            application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun checkSignInStatus() {
        viewModelScope.launch {
            _appState.value = AppState.Loading

            // Try to load cached events first for offline support
            val cachedEvents = loadCachedEvents()
            if (cachedEvents != null && cachedEvents.isNotEmpty()) {
                Log.d("CountdownViewModel", "Using cached events while checking sign-in")
                updateEventList(cachedEvents)
            }

            // Then try to fetch fresh data from Google Calendar
            val account = GoogleSignIn.getLastSignedInAccount(getApplication())
            if (account != null && GoogleSignIn.hasPermissions(account, Scope("https://www.googleapis.com/auth/calendar.readonly"))) {
                fetchCalendarEvents(account)
            } else {
                // If not signed in and no cached events, show sign-in screen
                if (cachedEvents == null || cachedEvents.isEmpty()) {
                    _appState.value = AppState.NeedsSignIn
                }
            }
        }
    }

    suspend fun getSignInToken(account: GoogleSignInAccount) {
        // The auth code is now a simple property on the account object.
        // It can be null if something went wrong or if it wasn't requested.
        val authCode: String? = account.serverAuthCode

        if (authCode != null) {
            // Now you have the auth code. You would typically send this
            // to your backend server, which then exchanges it for tokens.
            // For your client-side-only app, you might not need this flow
            // and can continue using GoogleAccountCredential as before.
            Log.d("Auth", "Server Auth Code: $authCode")

            // If your goal is just to make client-side calls, you still
            // use GoogleAccountCredential, which handles token management internally.
            // The requestServerSideAccess flow is primarily for backend integration.
        } else {
            Log.e("Auth", "Server Auth Code was null. Did you request it in GoogleSignInOptions?")
        }
    }

    // In CountdownViewModel.kt

    private fun fetchCalendarEvents(account: GoogleSignInAccount) {
        viewModelScope.launch {
            try {
                val accessToken = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        getApplication(),
                        account.account!!,
                        "oauth2:${Scope(CalendarScopes.CALENDAR_READONLY).scopeUri}"
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

                // Save to cache for offline access
                saveEventsToCache(uiEvents)

                val now = System.currentTimeMillis()
                val activeEvent = uiEvents.firstOrNull { now >= it.startTimeMillis && now < it.endTimeMillis }

                if (activeEvent != null) {
                    val remainingDuration = activeEvent.endTimeMillis - now
                    startCountdown(remainingDuration, activeEvent.title)
                } else {
                    _appState.value = AppState.ShowEventList(uiEvents)
                }
            } catch (e: Exception) {
                Log.e("CountdownViewModel", "Error fetching calendar events", e)
                // Try to use cached events on error
                val cachedEvents = loadCachedEvents()
                if (cachedEvents != null && cachedEvents.isNotEmpty()) {
                    Log.d("CountdownViewModel", "Using cached events after fetch error")
                    updateEventList(cachedEvents)
                } else {
                    _appState.value = AppState.NeedsSignIn
                }
            }
        }
    }

    /**
     * Updates the event list with synced events from the mobile app
     */
    fun updateEventList(events: List<UiEvent>) {
        // Save events to local storage
        viewModelScope.launch {
            saveEventsToCache(events)
        }

        val now = System.currentTimeMillis()
        val activeEvent = events.firstOrNull { now >= it.startTimeMillis && now < it.endTimeMillis }

        if (activeEvent != null) {
            val remainingDuration = activeEvent.endTimeMillis - now
            startCountdown(remainingDuration, activeEvent.title)
        } else {
            _appState.value = AppState.ShowEventList(events)
        }
    }

    /**
     * Save events to DataStore for offline access
     */
    private suspend fun saveEventsToCache(events: List<UiEvent>) {
        val context = getApplication<Application>().applicationContext
        val eventsJson = Json.encodeToString(events)
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("cached_events")] = eventsJson
        }
        Log.d("CountdownViewModel", "Saved ${events.size} events to cache")
    }

    /**
     * Load cached events from DataStore
     */
    private suspend fun loadCachedEvents(): List<UiEvent>? {
        val context = getApplication<Application>().applicationContext
        return try {
            val eventsJson = context.dataStore.data
                .map { preferences -> preferences[stringPreferencesKey("cached_events")] }
                .first()

            if (eventsJson != null) {
                val events = Json.decodeFromString<List<UiEvent>>(eventsJson)
                Log.d("CountdownViewModel", "Loaded ${events.size} events from cache")
                events
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("CountdownViewModel", "Failed to load cached events", e)
            null
        }
    }

    fun startCountdownForEvent(event: UiEvent) {
        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        eventStartPendingIntent?.let { alarmManager.cancel(it) }

        val now = System.currentTimeMillis()
        val durationMillis = event.endTimeMillis - event.startTimeMillis

        if (durationMillis <= 0) {
            checkSignInStatus()
            return
        }

        if (now < event.startTimeMillis) {
            _appState.value = AppState.WaitingForEvent(_currentTime.value, event.title)
            val intent = Intent(getApplication(), EventStartReceiver::class.java).apply {
                putExtra("eventTitle", event.title)
                putExtra("duration", durationMillis)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                getApplication(),
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            eventStartPendingIntent = pendingIntent
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, event.startTimeMillis, pendingIntent)
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
        val intent = Intent(getApplication(), TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_DURATION_MS, durationMillis)
            putExtra(TimerService.EXTRA_EVENT_TITLE, eventTitle)
        }
        getApplication<Application>().startService(intent)
    }

    private fun handleTimerState(state: TimerService.TimerState) {
        when (state) {
            is TimerService.TimerState.Counting -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(state.remainingTime)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(state.remainingTime) % 60
                val timeString = String.format("%02d:%02d", minutes, seconds)
                _appState.value = AppState.ShowCountdown(
                    progress = state.progress,
                    time = timeString,
                    currentTime = _currentTime.value,
                    eventTitle = state.eventTitle
                )
            }
            TimerService.TimerState.Finished -> {
                triggerNotification()
                checkSignInStatus()
            }
            TimerService.TimerState.Idle -> {
                checkSignInStatus()
            }
        }
    }

    fun stopCountdown() {
        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        eventStartPendingIntent?.let { alarmManager.cancel(it) }
        val intent = Intent(getApplication(), TimerService::class.java).apply {
            action = TimerService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    private fun getClientId(): String {
        // IMPORTANT: Replace this with your own Web application client ID from the Google Cloud Console.
        // This is required to get an access token to call the Google Calendar API.
        // It should look like: "YOUR_CLIENT_ID.apps.googleusercontent.com"
        return "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
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
        if (isBound) {
            getApplication<Application>().unbindService(connection)
            isBound = false
        }
        clockTimer?.cancel()
    }
}