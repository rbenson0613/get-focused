package com.example.get_focused.locktask

import kotlinx.serialization.Serializable

@Serializable
data class LockTaskSchedule(
    val id: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isEnabled: Boolean = true,
    val repeatDays: List<DayOfWeek> = emptyList()
)

@Serializable
enum class DayOfWeek {
    SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY
}

@Serializable
data class DeviceSettings(
    val isDeviceOwnerEnabled: Boolean,
    val lockTaskSchedules: List<LockTaskSchedule>,
    val allowedApps: List<String> = emptyList(),
    val restrictionsEnabled: Map<String, Boolean> = emptyMap()
)

@Serializable
data class UnlockRequest(
    val scheduleId: String,
    val timestamp: Long,
    val reason: String? = null
)