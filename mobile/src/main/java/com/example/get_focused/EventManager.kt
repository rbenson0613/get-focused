package com.example.get_focused

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Simple data class to hold lock details
data class LockData(
    val title: String,
    val startTime: Long,
    val endTime: Long
)

object EventManager {
    val events = mutableListOf(
        DashboardItem("Leave Home", R.drawable.ic_event_placeholder),
        DashboardItem("Arrive Work", R.drawable.ic_event_placeholder),
        DashboardItem("Meeting", R.drawable.ic_event_placeholder)
    )

    // Used for the UI list
    val locks = mutableListOf(
        DashboardItem("Study Time", R.drawable.ic_lock_placeholder),
        DashboardItem("Bedtime", R.drawable.ic_lock_placeholder)
    )

    // Used for the actual logic
    val activeLocksData = mutableListOf<LockData>()

    fun addEvent(title: String, date: String, time: String, duration: String, description: String) {
        events.add(0, DashboardItem(title, R.drawable.ic_event_placeholder))
    }

    fun addLock(title: String, date: String, start: String, end: String, isRepeating: Boolean) {
        // 1. Add to UI list
        locks.add(0, DashboardItem(title, R.drawable.ic_lock_placeholder))

        // 2. Parse times and add to logical list
        try {
            // Assuming input format like "yyyy-MM-dd" and "HH:mm"
            // You might need to adjust patterns to match your exact EditText input
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            // specific date is required for single event, but for simplicity we combine them
            // If date is empty (repeating), we might assume TODAY for testing.
            val effectiveDateStr = if(date.isBlank()) java.time.LocalDate.now().format(dateFormatter) else date

            val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

            val startDt = LocalDateTime.parse("$effectiveDateStr $start", dateTimeFormatter)
            val endDt = LocalDateTime.parse("$effectiveDateStr $end", dateTimeFormatter)

            val startTimeMillis = startDt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTimeMillis = endDt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

            activeLocksData.add(LockData(title, startTimeMillis, endTimeMillis))

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: If parsing fails, we don't add to activeLocksData to avoid crashes
        }
    }
}