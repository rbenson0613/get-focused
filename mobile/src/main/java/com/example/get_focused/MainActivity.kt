package com.example.get_focused

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var durationEditText: EditText
    private lateinit var startButton: Button
    private lateinit var calendarButton: Button
    private val dataClient by lazy { Wearable.getDataClient(this) }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openCalendar()
            }
        }

    private val selectEventLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    val cursor = contentResolver.query(uri, arrayOf(CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND), null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val startTime = it.getLong(0)
                            val endTime = it.getLong(1)
                            val duration = endTime - startTime
                            scheduleTimer(startTime, duration)
                        }
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        durationEditText = findViewById(R.id.durationEditText)
        startButton = findViewById(R.id.startButton)
        calendarButton = findViewById(R.id.calendarButton)

        startButton.setOnClickListener {
            val durationMinutes = durationEditText.text.toString().toLongOrNull()
            if (durationMinutes != null) {
                val durationMillis = TimeUnit.MINUTES.toMillis(durationMinutes)
                sendTimerData(durationMillis)
            }
        }

        calendarButton.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openCalendar()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
            }
        }
    }

    private fun openCalendar() {
        val intent = Intent(Intent.ACTION_PICK, CalendarContract.Events.CONTENT_URI)
        selectEventLauncher.launch(intent)
    }

    private fun scheduleTimer(startTime: Long, duration: Long) {
        if (duration > 0) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, TimerBroadcastReceiver::class.java).apply {
                putExtra("duration", duration)
            }
            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, startTime, pendingIntent)
        }
    }

    private fun sendTimerData(duration: Long) {
        val putDataMapRequest = PutDataMapRequest.create("/countdown")
        putDataMapRequest.dataMap.putLong("duration", duration)
        val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataRequest)
    }
}