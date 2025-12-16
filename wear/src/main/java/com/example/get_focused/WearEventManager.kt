package com.example.get_focused

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object WearEventManager {
    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events = _events.asStateFlow()

    fun updateEvents(newEvents: List<String>) {
        _events.value = newEvents
    }
}
