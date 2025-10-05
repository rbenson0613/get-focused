package com.example.get_focused.calendar

import com.google.gson.annotations.SerializedName

/**
 * Data classes that model the JSON response from the Google Calendar API's event list endpoint.
 * Using @SerializedName to map JSON keys to Kotlin properties.
 */

data class CalendarEventList(
    @SerializedName("items")
    val items: List<CalendarEvent> = emptyList()
)

data class CalendarEvent(
    @SerializedName("summary")
    val summary: String?,

    @SerializedName("start")
    val start: EventDateTime?,

    @SerializedName("end")
    val end: EventDateTime?
)

data class EventDateTime(
    // The value is an RFC3339 timestamp, e.g., "2025-10-05T10:00:00-07:00"
    @SerializedName("dateTime")
    val dateTime: String?
)