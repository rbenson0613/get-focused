package com.example.get_focused

object EventManager {
    val events = mutableListOf(
        DashboardItem("Leave Home", R.drawable.ic_event_placeholder),
        DashboardItem("Arrive Work", R.drawable.ic_event_placeholder),
        DashboardItem("Meeting", R.drawable.ic_event_placeholder)
    )

    val locks = mutableListOf(
        DashboardItem("Study Time", R.drawable.ic_lock_placeholder),
        DashboardItem("Bedtime", R.drawable.ic_lock_placeholder)
    )

    fun addEvent(title: String, date: String, time: String, duration: String, description: String) {
        // Add to the beginning of the list so it's visible
        events.add(0, DashboardItem(title, R.drawable.ic_event_placeholder))
        // In a real app, we would save the full details (date, time, etc.) to a database here.
    }

    fun addLock(title: String, date: String, start: String, end: String, isRepeating: Boolean) {
        locks.add(0, DashboardItem(title, R.drawable.ic_lock_placeholder))
        // In a real app, we would save the full details here.
    }
}
