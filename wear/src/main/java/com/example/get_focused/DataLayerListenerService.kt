package com.example.get_focused

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DataLayerListenerService : WearableListenerService() {
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/sync-events") {
            val payload = String(messageEvent.data)
            val eventTitles = payload.split("|").filter { it.isNotEmpty() }

            scope.launch {
                WearEventManager.updateEvents(eventTitles)
            }
        } else if (messageEvent.path == "/sync-locks") {
            val payload = String(messageEvent.data)
            // Format: Title|Start|End;Title|Start|End

            val parsedLocks = payload.split(";").mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size == 3) {
                    try {
                        WearLock(
                            title = parts[0],
                            startTime = parts[1].toLong(),
                            endTime = parts[2].toLong()
                        )
                    } catch (e: NumberFormatException) {
                        null
                    }
                } else {
                    null
                }
            }

            scope.launch {
                WearEventManager.updateLocks(parsedLocks)
            }
        }
    }
}