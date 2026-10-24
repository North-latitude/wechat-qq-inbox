package com.local.unifiedinbox.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.local.unifiedinbox.R
import com.local.unifiedinbox.model.HitLogEntry
import com.local.unifiedinbox.util.TimeFormat

class HitLogAdapter : RecyclerView.Adapter<HitLogAdapter.HitLogHolder>() {

    private val items = ArrayList<HitLogEntry>()

    fun submit(newItems: List<HitLogEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HitLogHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hit_log, parent, false)
        return HitLogHolder(view)
    }

    override fun onBindViewHolder(holder: HitLogHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class HitLogHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tvHitTime)
        private val tvConversation: TextView = itemView.findViewById(R.id.tvHitConversation)
        private val tvRules: TextView = itemView.findViewById(R.id.tvHitRules)

        fun bind(entry: HitLogEntry) {
            val context = itemView.context
            tvTime.text = context.getString(
                R.string.hit_log_time,
                TimeFormat.local(entry.createdAt)
            )
            tvConversation.text = context.getString(
                R.string.hit_log_conversation,
                entry.conversation
            )
            tvRules.text = context.getString(R.string.hit_log_rules, entry.rules)
        }
    }
}
