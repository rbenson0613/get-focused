package com.example.get_focused

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var eventsRecycler: RecyclerView
    private lateinit var locksRecycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Header Navigation
        findViewById<ImageView>(R.id.icon_account).setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }
        findViewById<ImageView>(R.id.icon_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<ImageView>(R.id.icon_add).setOnClickListener {
            startActivity(Intent(this, AddSelectionActivity::class.java))
        }

        // Initialize Recyclers
        eventsRecycler = findViewById(R.id.recycler_events)
        eventsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        locksRecycler = findViewById(R.id.recycler_locks)
        locksRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    override fun onResume() {
        super.onResume()
        refreshLists()
    }

    private fun refreshLists() {
        // Refresh Events List
        eventsRecycler.adapter = DashboardAdapter(EventManager.events) {
            startActivity(Intent(this, EventListActivity::class.java))
        }

        // Refresh Locks List
        locksRecycler.adapter = DashboardAdapter(EventManager.locks) {
            startActivity(Intent(this, LockListActivity::class.java))
        }
    }
}
