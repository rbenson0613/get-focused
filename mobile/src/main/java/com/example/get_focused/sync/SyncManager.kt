package com.example.get_focused.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SyncManager(context: Context) {

    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }
    private val scope = CoroutineScope(Dispatchers.IO)

    fun requestSync() {
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.d(TAG, "No connected wearable nodes found.")
                }
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, "/sync-events", ByteArray(0))
                        .addOnSuccessListener {
                            Log.d(TAG, "Sync message sent to ${node.displayName}")
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Failed to send sync message to ${node.displayName}", it)
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting sync", e)
            }
        }
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}