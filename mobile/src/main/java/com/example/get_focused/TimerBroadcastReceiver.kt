package com.example.get_focused

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class TimerBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val duration = intent.getLongExtra("duration", 0L)
        if (duration > 0) {
            val putDataMapRequest = PutDataMapRequest.create("/countdown")
            putDataMapRequest.dataMap.putLong("duration", duration)
            val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(putDataRequest)
        }
    }
}
