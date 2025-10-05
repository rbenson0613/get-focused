package com.example.get_focused.calendar

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CalendarManager {

    private const val TAG = "CalendarManager"
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun getUpcomingEvents(accessToken: String): List<CalendarEvent> {
        // The Google Calendar API requires the time to be in RFC3339 format.
        val timeMin = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val encodedTimeMin = URLEncoder.encode(timeMin, "UTF-8")

        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?" +
                "maxResults=10&" +
                "orderBy=startTime&" +
                "singleEvents=true&" +
                "timeMin=$encodedTimeMin"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch events: ${response.code} ${response.message}")
                    return@withContext emptyList()
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.w(TAG, "Response body is null or empty.")
                    return@withContext emptyList()
                }

                val eventList = gson.fromJson(responseBody, CalendarEventList::class.java)
                return@withContext eventList.items

            } catch (e: IOException) {
                Log.e(TAG, "Error fetching calendar events", e)
                return@withContext emptyList()
            }
        }
    }
}