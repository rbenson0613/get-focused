package com.example.get_focused

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.api.services.calendar.model.Event
import java.text.SimpleDateFormat
import java.util.Locale

// ✅ Change the primary constructor to accept a read-only List
class EventAdapter(eventsList: List<Event>) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    // ✅ Convert the incoming list to a mutable list for internal use
    private val events: MutableList<Event> = eventsList.toMutableList()

    // No longer need a secondary constructor

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.event_title_text)
        val timeTextView: TextView = view.findViewById(R.id.event_time_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(v)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.titleTextView.text = event.summary ?: "(no title)"

        val start = event.start
        val end = event.end
        val startMillis = start?.dateTime?.value ?: start?.date?.value ?: 0L
        val endMillis = end?.dateTime?.value ?: end?.date?.value ?: 0L

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        if (start?.dateTime != null && startMillis > 0L) {
            holder.timeTextView.text = "${timeFormat.format(startMillis)} - ${timeFormat.format(endMillis)}"
        } else if (start?.date != null && startMillis > 0L) {
            holder.timeTextView.text = "${dateFormat.format(startMillis)} - ${dateFormat.format(endMillis)}"
        } else {
            holder.timeTextView.text = ""
        }
    }

    override fun getItemCount(): Int = events.size

    fun updateEvents(newEvents: List<Event>) {
        events.clear()
        events.addAll(newEvents)
        notifyDataSetChanged()
    }
}