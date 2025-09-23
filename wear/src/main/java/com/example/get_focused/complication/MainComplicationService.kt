package com.example.get_focused.complication

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.example.get_focused.CountdownService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainComplicationService : SuspendingComplicationDataSourceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var countdownService: CountdownService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CountdownService.LocalBinder
            countdownService = binder.getService()
            isBound = true
            val updateRequester = ComplicationDataSourceUpdateRequester.create(
                this@MainComplicationService,
                ComponentName(
                    this@MainComplicationService,
                    MainComplicationService::class.java
                )
            )
            updateRequester.requestUpdateAll()

            scope.launch {
                countdownService?.isRunning?.collect {
                    updateRequester.requestUpdateAll()
                }
            }
            scope.launch {
                countdownService?.isPaused?.collect {
                    updateRequester.requestUpdateAll()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            countdownService = null
            isBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        Intent(this, CountdownService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE) {
            return null
        }
        return RangedValueComplicationData.Builder(
            value = 50f,
            min = 0f,
            max = 100f,
            contentDescription = PlainComplicationText.Builder("Countdown").build()
        ).setText(PlainComplicationText.Builder("10:00").build()).build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val remainingTime = countdownService?.remainingTime?.first() ?: 0L
        val duration = countdownService?.duration?.first() ?: 0L
        val percentage = if (duration > 0) (remainingTime.toFloat() / duration) * 100 else 0f

        return RangedValueComplicationData.Builder(
            value = percentage,
            min = 0f,
            max = 100f,
            contentDescription = PlainComplicationText.Builder("Countdown").build()
        ).setText(PlainComplicationText.Builder(formatTime(remainingTime)).build()).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}