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
            val lockTitles = payload.split("|").filter { it.isNotEmpty() }

            scope.launch {
                WearEventManager.updateLocks(lockTitles)
            }
        }
    }
}
