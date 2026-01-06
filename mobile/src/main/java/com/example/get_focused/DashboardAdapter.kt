package com.example.get_focused

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DashboardItem(val title: String, val iconResId: Int)

class DashboardAdapter(
    private val items: List<DashboardItem>,
    private val onMoreClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_MORE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_MORE else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ITEM) {
            val view = inflater.inflate(R.layout.item_event_tile, parent, false)
            ItemViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_event_tile, parent, false)
            MoreViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) {
            holder.bind(items[position])
        } else if (holder is MoreViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int = items.size + 1

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.icon_image)
        private val title: TextView = itemView.findViewById(R.id.text_title)

        fun bind(item: DashboardItem) {
            title.text = item.title
            icon.setImageResource(item.iconResId)
        }
    }

    inner class MoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.icon_image)
        private val title: TextView = itemView.findViewById(R.id.text_title)

        fun bind() {
            title.text = "More"
            // Using a generic arrow or ellipsis icon for "More"
            // If ic_chevron_right is available use it, otherwise fallback
            icon.setImageResource(R.drawable.ic_chevron_right)
            itemView.setOnClickListener { onMoreClicked() }
        }
    }
}
