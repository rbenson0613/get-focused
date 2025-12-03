package com.example.get_focused

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.get_focused.locktask.LockTaskSchedule
import java.text.SimpleDateFormat
import java.util.*

class LockTaskScheduleAdapter(
    private val schedules: List<LockTaskSchedule>,
    private val onToggle: (LockTaskSchedule, Boolean) -> Unit,
    private val onDelete: (LockTaskSchedule) -> Unit,
    private val onEdit: (LockTaskSchedule) -> Unit
) : RecyclerView.Adapter<LockTaskScheduleAdapter.ScheduleViewHolder>() {

    class ScheduleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.schedule_title)
        val timeText: TextView = view.findViewById(R.id.schedule_time)
        val enabledSwitch: Switch = view.findViewById(R.id.schedule_enabled_switch)
        val deleteButton: ImageButton = view.findViewById(R.id.schedule_delete_button)
        val editButton: ImageButton = view.findViewById(R.id.schedule_edit_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lock_task_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val schedule = schedules[position]
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        holder.titleText.text = schedule.title
        holder.timeText.text = "${timeFormat.format(Date(schedule.startTime))} - ${timeFormat.format(Date(schedule.endTime))}"
        holder.enabledSwitch.isChecked = schedule.isEnabled

        holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggle(schedule, isChecked)
        }

        holder.deleteButton.setOnClickListener {
            onDelete(schedule)
        }

        holder.editButton.setOnClickListener {
            onEdit(schedule)
        }
    }

    override fun getItemCount() = schedules.size
}