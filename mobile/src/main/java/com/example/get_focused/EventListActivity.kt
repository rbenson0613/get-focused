package com.example.get_focused

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.common.api.ApiException
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.get_focused.calendar.CalendarManager
import com.example.get_focused.sync.SyncManager
import com.example.get_focused.sync.SyncedEvent
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.calendar.CalendarScopes
import androidx.recyclerview.widget.RecyclerView


class EventListActivity : AppCompatActivity() {

    private val TAG = "EventListActivity"

    // Keep a reference to the signed-in account (optional, but useful)
    private var googleAccount: GoogleSignInAccount? = null

    // GoogleSignInClient instance (initialized in onCreate)
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter
    private val syncManager: SyncManager by lazy { SyncManager(this) }


    // ActivityResult launcher for the sign-in Intent
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "Sign-in activity result: ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                val task: Task<GoogleSignInAccount> =
                    GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    // task.getResult can throw ApiException
                    val account = task.getResult(ApiException::class.java)
                    handleSignInSuccess(account)
                } catch (e: ApiException) {
                    Log.w(TAG, "Google sign-in failed: ${e.statusCode}", e)
                    showToast("Sign-in failed. Please try again.")
                }
            } else {
                Log.d(TAG, "Sign-in cancelled or failed with code ${result.resultCode}")
                showToast("Sign-in cancelled.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_list)

        // --- Setup RecyclerView ---
        eventsRecyclerView = findViewById(R.id.events_recycler_view)
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Add a divider
        val dividerItemDecoration = DividerItemDecoration(
            eventsRecyclerView.context,
            (eventsRecyclerView.layoutManager as LinearLayoutManager).orientation
        )
        eventsRecyclerView.addItemDecoration(dividerItemDecoration)


        // --- Build GoogleSignInOptions with Calendar events scope and email ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // add the calendar events scope; adjust if you need a different calendar scope
            .requestScopes(Scope(CalendarScopes.CALENDAR))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Try silent sign in first (more robust than only getLastSignedInAccount)
        attemptSilentSignIn()

        // Setup UI & FAB - keep existing code / adapter setup but replace FAB click handler:
        val syncEventsFab: FloatingActionButton = findViewById(R.id.sync_events_fab)
        syncEventsFab.setOnClickListener {
            // Always check the canonical source at time-of-click
            val currentAccount = GoogleSignIn.getLastSignedInAccount(this)
            if (currentAccount != null) {
                Log.d(TAG, "Manual sync: using account ${currentAccount.email}")
                this.googleAccount = currentAccount
                fetchEvents(currentAccount)
                showToast("Events synced!")
            } else {
                Log.d(TAG, "Manual sync: no signed in account; requesting sign-in.")
                showToast("Please sign in first.")
                requestSignIn()
            }
        }

        val addEventFab: FloatingActionButton = findViewById(R.id.add_event_fab)
        addEventFab.setOnClickListener {
            val intent = Intent(this, AddEventActivity::class.java)
            addEventLauncher.launch(intent)
        }
    }

    private fun attemptSilentSignIn() {
        val TAG = "EventListActivity"
        Log.d(TAG, "Attempting silent sign-in...")

        val silentSignInTask = googleSignInClient.silentSignIn()
        silentSignInTask
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Successfully signed in silently
                    val account = task.result
                    Log.d(TAG, "Silent sign-in successful for ${account?.email}")
                    if (account != null) {
                        handleSignInSuccess(account)
                    } else {
                        Log.w(TAG, "Silent sign-in returned null account — requesting interactive sign-in")
                        requestSignIn()
                    }
                } else {
                    // Silent sign-in failed: handle it safely
                    val e = task.exception
                    if (e is ApiException) {
                        Log.w(TAG, "Silent sign-in failed with code ${e.statusCode}: ${e.message}")
                    } else {
                        Log.w(TAG, "Silent sign-in failed", e)
                    }
                    requestSignIn() // Launch interactive sign-in
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Silent sign-in threw exception", e)
                requestSignIn()
            }
    }



    private fun requestSignIn() {
        Log.d(TAG, "Starting interactive sign-in intent")
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun handleSignInSuccess(account: GoogleSignInAccount?) {
        if (account == null) {
            Log.w(TAG, "handleSignInSuccess called with null account")
            showToast("Sign-in failed.")
            return
        }

        Log.d(TAG, "Sign-in success for: ${account.email}; id=${account.id}")
        this.googleAccount = account

        // Immediately fetch events after sign-in
        fetchEvents(account)
        showToast("Signed in successfully — events synced.")
    }

    private val addEventLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                googleAccount?.let { fetchEvents(it) }
            }
        }


    private fun fetchEvents(account: GoogleSignInAccount) {
        val TAG = "EventListActivity"
        Log.d(TAG, "fetchEvents() starting for account=${account.email}")

        val credential = GoogleAccountCredential.usingOAuth2(
            this,
            listOf(CalendarScopes.CALENDAR)
        ).setSelectedAccount(account.account)

        lifecycleScope.launch {
            try {
                //Log.d(TAG, "Calling CalendarManager.getUpcomingEvents()...")
                //val events = CalendarManager.getUpcomingEvents(credential)
                //Log.d(TAG, "CalendarManager returned ${events.size} events")
                val events = emptyList<com.google.api.services.calendar.model.Event>()
                Log.d(TAG, "Sync disabled: Returning empty event list")
                // Initialize or update adapter
                if (::eventAdapter.isInitialized) {
                    // Option A: if your adapter supports update, use it (preferred)
                    try {
                        eventAdapter.updateEvents(events)
                        Log.d(TAG, "Adapter updated with ${events.size} events")
                    } catch (e: NoSuchMethodError) {
                        // Fallback if adapter doesn't have update method: recreate adapter
                        eventAdapter = EventAdapter(events)
                        eventsRecyclerView.adapter = eventAdapter
                        Log.d(TAG, "Adapter recreated with ${events.size} events")
                    }
                } else {
                    eventAdapter = EventAdapter(events)
                    eventsRecyclerView.adapter = eventAdapter
                    Log.d(TAG, "Adapter created with ${events.size} events")
                }

                // ✅ Convert the google Event list to a list of SyncedEvent
                val syncedEvents = events.mapNotNull { event ->
                    // An event must have an ID to be syncable
                    event.id?.let { id ->
                        SyncedEvent(
                            id = id,
                            title = event.summary ?: "(No Title)",
                            startTime = event.start?.dateTime?.value ?: event.start?.date?.value ?: 0L,
                            endTime = event.end?.dateTime?.value ?: event.end?.date?.value
                        )
                    }
                }

                // ✅ Pass the converted list to the sync manager
                try {
                    syncManager.requestSync(syncedEvents)
                    Log.d(TAG, "syncManager.requestSync() called with ${syncedEvents.size} events")
                } catch (e: Exception) {
                    Log.w(TAG, "syncManager.requestSync() failed", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while fetching events", e)
                showToast("Failed to fetch events: ${e.message}")
            }
        }
    }


    // Simple toast helper
    private fun showToast(msg: String) {
        runOnUiThread {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

}