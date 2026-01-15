package com.example.get_focused

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WearLock(
    val title: String,
    val startTime: Long,
    val endTime: Long
)

object WearEventManager {
    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events = _events.asStateFlow()

    private val _locks = MutableStateFlow<List<WearLock>>(emptyList())
    val locks = _locks.asStateFlow()

    fun updateEvents(newEvents: List<String>) {
        _events.value = newEvents
    }

    fun updateLocks(newLocks: List<WearLock>) {
        _locks.value = newLocks
    }
}