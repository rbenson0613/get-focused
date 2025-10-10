package com.example.get_focused

import android.app.Activity
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.get_focused.calendar.CalendarManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddEventActivity : AppCompatActivity() {

    private lateinit var eventTitle: EditText
    private lateinit var startTimeText: TextView
    private lateinit var endTimeText: TextView
    private lateinit var createEventButton: Button

    private val startCalendar = Calendar.getInstance()
    private val endCalendar = Calendar.getInstance()

    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(this) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        eventTitle = findViewById(R.id.event_title)
        startTimeText = findViewById(R.id.start_time_text)
        endTimeText = findViewById(R.id.end_time_text)
        createEventButton = findViewById(R.id.create_event_button)

        setupTimePickers()
        updateTimeLabels()

        createEventButton.setOnClickListener {
            handleCreateEventClick()
        }
    }

    private fun setupTimePickers() {
        startTimeText.setOnClickListener {
            showTimePickerDialog(startCalendar) {
                updateTimeLabels()
            }
        }

        endTimeText.setOnClickListener {
            showTimePickerDialog(endCalendar) {
                updateTimeLabels()
            }
        }
    }

    private fun showTimePickerDialog(calendar: Calendar, onTimeSet: () -> Unit) {
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            onTimeSet()
        }
        TimePickerDialog(
            this,
            timeSetListener,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun updateTimeLabels() {
        startTimeText.text = timeFormatter.format(startCalendar.time)
        endTimeText.text = timeFormatter.format(endCalendar.time)
    }

    private fun handleCreateEventClick() {
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (lastSignedInAccount == null || !hasCalendarPermissions(lastSignedInAccount)) {
            requestSignIn()
        } else {
            createCalendarEvent(lastSignedInAccount)
        }
    }

    private fun hasCalendarPermissions(account: GoogleSignInAccount): Boolean {
        return GoogleSignIn.hasPermissions(account, Scope(CalendarScopes.CALENDAR_EVENTS))
    }

    private fun requestSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_EVENTS))
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.result
                    createCalendarEvent(account)
                } catch (e: Exception) {
                    showToast("Sign-in failed. Please try again.")
                }
            }
        }

    private fun createCalendarEvent(account: GoogleSignInAccount) {
        val title = eventTitle.text.toString()
        if (title.isBlank()) {
            showToast("Please enter an event title.")
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            this,
            listOf(CalendarScopes.CALENDAR_EVENTS)
        ).setSelectedAccount(account.account)

        lifecycleScope.launch {
            val success = CalendarManager.createEvent(
                credential,
                title,
                startCalendar.timeInMillis,
                endCalendar.timeInMillis
            )
            if (success) {
                showToast("Event created successfully!")
                notifyWatchToSync()
            } else {
                showToast("Failed to create event.")
            }
        }
    }

    private fun notifyWatchToSync() {
        lifecycleScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, "/sync-events", ByteArray(0))
                        .addOnSuccessListener {
                            Log.d("MainActivity", "Sync message sent to ${node.displayName}")
                        }
                        .addOnFailureListener {
                            Log.e("MainActivity", "Failed to send sync message", it)
                        }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting connected nodes", e)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}