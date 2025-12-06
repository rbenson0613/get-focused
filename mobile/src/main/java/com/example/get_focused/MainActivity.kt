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

        // Setup Events List
        val eventsRecycler = findViewById<RecyclerView>(R.id.recycler_events)
        val eventItems = listOf(
            DashboardItem("Leave Home", R.drawable.ic_event_placeholder),
            DashboardItem("Arrive Work", R.drawable.ic_event_placeholder),
            DashboardItem("Meeting", R.drawable.ic_event_placeholder)
        )
        eventsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        eventsRecycler.adapter = DashboardAdapter(eventItems) {
            startActivity(Intent(this, EventListActivity::class.java))
        }

        // Setup Locks List
        val locksRecycler = findViewById<RecyclerView>(R.id.recycler_locks)
        val lockItems = listOf(
            DashboardItem("Study Time", R.drawable.ic_lock_placeholder),
            DashboardItem("Bedtime", R.drawable.ic_lock_placeholder)
        )
        locksRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        locksRecycler.adapter = DashboardAdapter(lockItems) {
            startActivity(Intent(this, LockListActivity::class.java))
        }
    }
}
