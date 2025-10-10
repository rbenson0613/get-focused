package com.example.get_focused

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.api.services.calendar.model.Event
import java.text.SimpleDateFormat
import java.util.Locale

class EventAdapter(private val events: List<Event>) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.event_title_text)
        val timeTextView: TextView = view.findViewById(R.id.event_time_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.titleTextView.text = event.summary
        val startTime = event.start.dateTime?.value ?: event.start.date.value
        val endTime = event.end.dateTime?.value ?: event.end.date.value
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        if (event.start.dateTime != null) {
            holder.timeTextView.text = "${timeFormat.format(startTime)} - ${timeFormat.format(endTime)}"
        } else {
            holder.timeTextView.text = "${dateFormat.format(startTime)} - ${dateFormat.format(endTime)}"
        }
    }

    override fun getItemCount() = events.size
}