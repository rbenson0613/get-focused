package com.example.get_focused

import android.content.Intent
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class DataLayerListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                val path = event.dataItem.uri.path
                if (path == "/countdown") {
                    val duration = dataMapItem.dataMap.getLong("duration")
                    val intent = Intent(this, CountdownService::class.java).apply {
                        putExtra(CountdownService.EXTRA_DURATION, duration)
                    }
                    startService(intent)
                }
            }
        }
    }
}
