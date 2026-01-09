package com.example.get_focused

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object SyncManager {
    private const val SYNC_PATH = "/sync-events"

    fun sendEvents(context: Context, events: List<DashboardItem>) {
        val messageClient = Wearable.getMessageClient(context)
        val nodeIdClient = Wearable.getNodeClient(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get all connected nodes (wearable devices)
                val nodes = nodeIdClient.connectedNodes.await()

                // Simple serialization: Join titles with "|"
                val payload = events.joinToString("|") { it.title }.toByteArray()

                for (node in nodes) {
                    messageClient.sendMessage(node.id, SYNC_PATH, payload).await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
