package com.example.get_focused

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object WearEventManager {
    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events = _events.asStateFlow()

    private val _locks = MutableStateFlow<List<String>>(emptyList())
    val locks = _locks.asStateFlow()

    fun updateEvents(newEvents: List<String>) {
        _events.value = newEvents
    }

    fun updateLocks(newLocks: List<String>) {
        _locks.value = newLocks
    }
}
