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
        countdownTimer?.cancel()
        eventStartTimer?.cancel()
        clockTimer?.cancel()
    }
}