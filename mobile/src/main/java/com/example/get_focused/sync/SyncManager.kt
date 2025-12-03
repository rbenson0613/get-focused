package com.example.get_focused.sync

import android.content.Context
import android.util.Log
import com.example.get_focused.locktask.DeviceSettings
import com.example.get_focused.locktask.LockTaskSchedule
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

class SyncManager(private val context: Context) {

    private val TAG = "SyncManager"
    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }

    /**
     * Sync calendar events to watch
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
                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "putDataItem success: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "putDataItem failed", e)
            }
        }
    }

    /**
     * Sync lock task schedules to watch
     */
    fun syncLockTaskSchedules(schedules: List<LockTaskSchedule>) {
        Log.d(TAG, "syncLockTaskSchedules() called with ${schedules.size} schedules")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedulesJson = Json.encodeToString(schedules)
                val putDataMapReq = PutDataMapRequest.create("/lock-task-schedules")
                putDataMapReq.dataMap.putString("schedules_json", schedulesJson)
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
                putDataMapReq.dataMap.putInt("schedule_count", schedules.size)

                val request = putDataMapReq.asPutDataRequest().setUrgent()
                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "Lock task schedules synced: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync lock task schedules", e)
            }
        }
    }

    /**
     * Sync device settings (restrictions, allowed apps, etc.)
     */
    fun syncDeviceSettings(settings: DeviceSettings) {
        Log.d(TAG, "syncDeviceSettings() called")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsJson = Json.encodeToString(settings)
                val putDataMapReq = PutDataMapRequest.create("/device-settings")
                putDataMapReq.dataMap.putString("settings_json", settingsJson)
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())

                val request = putDataMapReq.asPutDataRequest().setUrgent()
                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "Device settings synced: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync device settings", e)
            }
        }
    }

    /**
     * Send immediate unlock command to watch
     */
    suspend fun sendUnlockCommand(scheduleId: String): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes for unlock command")
                return false
            }

            val message = Json.encodeToString(mapOf(
                "command" to "unlock",
                "scheduleId" to scheduleId,
                "timestamp" to System.currentTimeMillis()
            ))

            for (node in nodes) {
                messageClient.sendMessage(
                    node.id,
                    "/unlock-command",
                    message.toByteArray()
                ).await()
                Log.d(TAG, "Unlock command sent to ${node.displayName}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send unlock command", e)
            false
        }
    }

    /**
     * Check if watch is connected
     */
    suspend fun isWatchConnected(): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check watch connection", e)
            false
        }
    }
}