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
import com.example.get_focused.sync.SyncManager
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.launch
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
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                showToast("Failed to create event.")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}