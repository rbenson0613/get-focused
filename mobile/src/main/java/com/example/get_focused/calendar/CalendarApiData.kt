package com.example.get_focused.calendar

data class CalendarEventList(
    val items: List<CalendarEvent>
)

data class CalendarEvent(
    val summary: String?,
    val start: EventDateTime?,
    val end: EventDateTime?
)

data class EventDateTime(
    val dateTime: String?
)