package com.example.get_focused

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.get_focused.calendar.CalendarManager
import com.example.get_focused.sync.SyncManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.launch

class EventListActivity : AppCompatActivity() {

    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter
    private var googleAccount: GoogleSignInAccount? = null
    private val syncManager: SyncManager by lazy { SyncManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_list)

        eventsRecyclerView = findViewById(R.id.events_recycler_view)
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        val dividerItemDecoration = DividerItemDecoration(eventsRecyclerView.context,
            (eventsRecyclerView.layoutManager as LinearLayoutManager).orientation)
        dividerItemDecoration.setDrawable(resources.getDrawable(R.drawable.divider, null))
        eventsRecyclerView.addItemDecoration(dividerItemDecoration)

        val addEventFab: com.google.android.material.floatingactionbutton.FloatingActionButton = findViewById(R.id.add_event_fab)
        addEventFab.setOnClickListener {
            addEventLauncher.launch(Intent(this, AddEventActivity::class.java))
        }

        val syncEventsFab: com.google.android.material.floatingactionbutton.FloatingActionButton = findViewById(R.id.sync_events_fab)
        syncEventsFab.setOnClickListener {
            googleAccount?.let {
                fetchEvents(it)
                showToast("Events synced!")
            } ?: showToast("Please sign in first.")
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            requestSignIn()
        } else {
            this.googleAccount = account
            fetchEvents(account)
        }
    }

    private fun requestSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR))
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    this.googleAccount = account
                    fetchEvents(account)
                } catch (e: ApiException) {
                    showToast("Sign-in failed. Please try again.")
                }
            }
        }

    private val addEventLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                googleAccount?.let { fetchEvents(it) }
            }
        }

    private fun fetchEvents(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            this,
            listOf(CalendarScopes.CALENDAR)
        ).setSelectedAccount(account.account)

        lifecycleScope.launch {
            val events = CalendarManager.getUpcomingEvents(credential)
            eventAdapter = EventAdapter(events)
            eventsRecyclerView.adapter = eventAdapter
            syncManager.requestSync()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}