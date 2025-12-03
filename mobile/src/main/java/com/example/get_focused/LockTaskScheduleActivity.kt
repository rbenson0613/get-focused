package com.example.get_focused

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.get_focused.locktask.LockTaskSchedule
import com.example.get_focused.sync.SyncManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText

val Context.lockTaskDataStore by preferencesDataStore(name = "lock_task_schedules")

class LockTaskScheduleActivity : AppCompatActivity() {

    private lateinit var schedulesRecyclerView: RecyclerView
    private lateinit var scheduleAdapter: LockTaskScheduleAdapter
    private lateinit var addScheduleFab: FloatingActionButton
    private val syncManager: SyncManager by lazy { SyncManager(this) }

    private val schedules = mutableListOf<LockTaskSchedule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_task_schedule)

        setupRecyclerView()
        setupFab()
        loadSchedules()
    }

    private fun setupRecyclerView() {
        schedulesRecyclerView = findViewById(R.id.schedules_recycler_view)
        schedulesRecyclerView.layoutManager = LinearLayoutManager(this)

        scheduleAdapter = LockTaskScheduleAdapter(
            schedules = schedules,
            onToggle = { schedule, enabled ->
                toggleSchedule(schedule, enabled)
            },
            onDelete = { schedule ->
                deleteSchedule(schedule)
            },
            onEdit = { schedule ->
                editSchedule(schedule)
            }
        )

        schedulesRecyclerView.adapter = scheduleAdapter
    }

    private fun setupFab() {
        addScheduleFab = findViewById(R.id.add_schedule_fab)
        addScheduleFab.setOnClickListener {
            showAddScheduleDialog()
        }
    }

    private fun loadSchedules() {
        lifecycleScope.launch {
            try {
                val schedulesJson = lockTaskDataStore.data
                    .map { prefs -> prefs[stringPreferencesKey("schedules")] }
                    .first()

                if (schedulesJson != null) {
                    val loadedSchedules = Json.decodeFromString<List<LockTaskSchedule>>(schedulesJson)
                    schedules.clear()
                    schedules.addAll(loadedSchedules)
                    scheduleAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LockTaskScheduleActivity, "Failed to load schedules", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSchedules() {
        lifecycleScope.launch {
            try {
                val schedulesJson = Json.encodeToString(schedules)
                lockTaskDataStore.edit { prefs ->
                    prefs[stringPreferencesKey("schedules")] = schedulesJson
                }

                // Sync to watch
                syncManager.syncLockTaskSchedules(schedules)
                Toast.makeText(this@LockTaskScheduleActivity, "Synced to watch", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@LockTaskScheduleActivity, "Failed to save schedules", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddScheduleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_schedule, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.schedule_title_input)
        val startTimeButton = dialogView.findViewById<Button>(R.id.start_time_button)
        val endTimeButton = dialogView.findViewById<Button>(R.id.end_time_button)

        val startCalendar = Calendar.getInstance()
        val endCalendar = Calendar.getInstance()
        endCalendar.add(Calendar.HOUR_OF_DAY, 1)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        startTimeButton.text = timeFormat.format(startCalendar.time)
        endTimeButton.text = timeFormat.format(endCalendar.time)

        startTimeButton.setOnClickListener {
            showTimePickerDialog(startCalendar) {
                startTimeButton.text = timeFormat.format(startCalendar.time)
            }
        }

        endTimeButton.setOnClickListener {
            showTimePickerDialog(endCalendar) {
                endTimeButton.text = timeFormat.format(endCalendar.time)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Add Lock Task Schedule")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString()
                if (title.isNotBlank()) {
                    val schedule = LockTaskSchedule(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        startTime = startCalendar.timeInMillis,
                        endTime = endCalendar.timeInMillis,
                        isEnabled = true
                    )
                    schedules.add(schedule)
                    scheduleAdapter.notifyItemInserted(schedules.size - 1)
                    saveSchedules()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimePickerDialog(calendar: Calendar, onTimeSet: () -> Unit) {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                onTimeSet()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun toggleSchedule(schedule: LockTaskSchedule, enabled: Boolean) {
        val index = schedules.indexOfFirst { it.id == schedule.id }
        if (index != -1) {
            schedules[index] = schedule.copy(isEnabled = enabled)
            scheduleAdapter.notifyItemChanged(index)
            saveSchedules()
        }
    }

    private fun deleteSchedule(schedule: LockTaskSchedule) {
        AlertDialog.Builder(this)
            .setTitle("Delete Schedule")
            .setMessage("Are you sure you want to delete '${schedule.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                val index = schedules.indexOfFirst { it.id == schedule.id }
                if (index != -1) {
                    schedules.removeAt(index)
                    scheduleAdapter.notifyItemRemoved(index)
                    saveSchedules()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editSchedule(schedule: LockTaskSchedule) {
        Toast.makeText(this, "Edit functionality coming soon", Toast.LENGTH_SHORT).show()
    }
}