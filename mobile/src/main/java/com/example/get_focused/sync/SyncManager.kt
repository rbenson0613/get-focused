package com.example.get_focused.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SyncedEvent(
    val id: String,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null
)


    private val TAG = "SyncManager"


    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Called when events are added or updated — triggers Wear OS sync
     */
    fun requestSync(events: List<SyncedEvent>) {
        Log.d(TAG, "requestSync() called — preparing DataItem with ${events.size} events")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val eventsJson = Json.encodeToString(events)
                val putDataMapReq = PutDataMapRequest.create("/sync-events")
                putDataMapReq.dataMap.putString("events_json", eventsJson)
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
                putDataMapReq.dataMap.putInt("event_count", events.size)

                val request = putDataMapReq.asPutDataRequest().setUrgent()
                val result = Wearable.getDataClient(context).putDataItem(request).await()
                Log.d(TAG, "putDataItem success: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "putDataItem failed", e)
            }
        }
    }


    companion object {
        private const val TAG = "SyncManager"
    }
}