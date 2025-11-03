package com.example.get_focused.sync

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json
import com.example.get_focused.presentation.ui.UiEvent
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import com.example.get_focused.data.eventsDataStore

class DataSyncService : WearableListenerService() {

    private val TAG = "DataSyncService"

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged called")
        try {
            for (event in dataEvents) {
                Log.d(TAG, "Data event type: ${event.type}, path: ${event.dataItem.uri.path}")

                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == "/sync-events") {

                    Log.d(TAG, "Sync event detected, processing data")
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val dataMap = dataMapItem.dataMap

                    val eventsJson = dataMap.getString("events_json")
                    val eventCount = dataMap.getInt("event_count", -1)

                    Log.d(TAG, "Received $eventCount events, caching them")

                    if (eventsJson != null) {
                        // Cache the events so they're available when app launches
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val syncedEvents = Json.decodeFromString<List<SyncedEvent>>(eventsJson)
                                val uiEvents = syncedEvents.mapNotNull { syncEvent ->
                                    syncEvent.endTime?.let { endTime ->
                                        UiEvent(
                                            title = syncEvent.title,
                                            startTimeMillis = syncEvent.startTime,
                                            endTimeMillis = endTime
                                        )
                                    }
                                }

                                // Save to cache
                                val uiEventsJson = Json.encodeToString(uiEvents)
                                applicationContext.eventsDataStore.edit { preferences ->
                                    preferences[stringPreferencesKey("cached_events")] = uiEventsJson
                                }
                                Log.d(TAG, "Cached ${uiEvents.size} events")

                                // Send broadcast to update UI if app is running
                                val intent = Intent(ACTION_SYNC_EVENTS)
                                LocalBroadcastManager.getInstance(this@DataSyncService).sendBroadcast(intent)
                                Log.d(TAG, "Broadcast sent to update UI")

                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to cache events", e)
                            }
                        }
                    }
                }
            }
        } finally {
            dataEvents.release()
        }
    }

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