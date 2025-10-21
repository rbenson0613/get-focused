package com.example.get_focused.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncedEvent(
    val id: String,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null
)