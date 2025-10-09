package com.example.get_focused.sync

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class DataSyncService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/sync-events") {
            val intent = Intent(ACTION_SYNC_EVENTS)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    companion object {
        const val ACTION_SYNC_EVENTS = "com.example.get_focused.SYNC_EVENTS"
    }
}