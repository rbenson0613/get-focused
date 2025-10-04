package com.example.get_focused.calendar

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

object CalendarManager {

    private var calendarService: Calendar? = null

    fun initialize(context: Context, account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(CalendarScopes.CALENDAR_READONLY)
        ).setSelectedAccount(account.account)

        calendarService = Calendar.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Get Focused")
            .build()
    }

    suspend fun getUpcomingEvents(): List<Event> {
        val service = calendarService ?: return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val now = com.google.api.client.util.DateTime(System.currentTimeMillis())
                service.events().list("primary")
                    .setMaxResults(10) // Fetch a list of up to 10 events
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()
                    .items ?: emptyList()
            } catch (e: Exception) {
                // Log the error
                e.printStackTrace()
                emptyList()
            }
        }
    }
}