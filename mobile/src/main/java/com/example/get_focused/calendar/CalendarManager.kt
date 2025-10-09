package com.example.get_focused.calendar

import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object CalendarManager {

    private const val TAG = "CalendarManager"

    suspend fun createEvent(
        credential: GoogleAccountCredential,
        title: String,
        startTime: Long,
        endTime: Long
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val transport = NetHttpTransport()
                val jsonFactory = GsonFactory.getDefaultInstance()
                val service = Calendar.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Get Focused")
                    .build()

                val event = Event().apply {
                    summary = title
                    start = EventDateTime().setDateTime(DateTime(startTime))
                    end = EventDateTime().setDateTime(DateTime(endTime))
                }

                service.events().insert("primary", event).execute()
                true
            } catch (e: IOException) {
                Log.e(TAG, "Error creating event", e)
                false
            }
        }
    }
}