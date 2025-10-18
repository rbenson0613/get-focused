package com.example.get_focused.sync

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class DataSyncService : WearableListenerService() {

    private val TAG = "DataSyncService"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: message received with path: ${messageEvent.path}")
        if (messageEvent.path == "/sync-events") {
            Log.d(TAG, "onMessageReceived: path matches, sending broadcast")
            val intent = Intent(ACTION_SYNC_EVENTS)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } else {
            Log.d(TAG, "onMessageReceived: path does not match, ignoring message")
        }
    }

    companion object {
        const val ACTION_SYNC_EVENTS = "com.example.get_focused.SYNC_EVENTS"
    }
}