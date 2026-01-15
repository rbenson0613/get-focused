package com.example.get_focused

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object SyncManager {
    private const val SYNC_EVENTS_PATH = "/sync-events"
    private const val SYNC_LOCKS_PATH = "/sync-locks"

    fun sendEvents(context: Context, events: List<DashboardItem>) {
        val messageClient = Wearable.getMessageClient(context)
        val nodeIdClient = Wearable.getNodeClient(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeIdClient.connectedNodes.await()
                val payload = events.joinToString("|") { it.title }.toByteArray()

                for (node in nodes) {
                    messageClient.sendMessage(node.id, SYNC_EVENTS_PATH, payload).await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendLocks(context: Context, locks: List<DashboardItem>) {
        val messageClient = Wearable.getMessageClient(context)
        val nodeIdClient = Wearable.getNodeClient(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeIdClient.connectedNodes.await()

                // SERIALIZE: "Title|StartMillis|EndMillis;Title2|..."
                // We use EventManager.activeLocksData because 'locks' (DashboardItems) doesn't have time info
                val payloadString = EventManager.activeLocksData.joinToString(";") {
                    "${it.title}|${it.startTime}|${it.endTime}"
                }

                val payload = payloadString.toByteArray()

                for (node in nodes) {
                    messageClient.sendMessage(node.id, SYNC_LOCKS_PATH, payload).await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}