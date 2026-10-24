package com.local.unifiedinbox.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.local.unifiedinbox.R
import com.local.unifiedinbox.model.Rule

class RuleAdapter(
    private val onToggle: (Rule, Boolean) -> Unit,
    private val onEdit: (Rule) -> Unit,
    private val onDelete: (Rule) -> Unit
) : RecyclerView.Adapter<RuleAdapter.RuleHolder>() {

    private val items = ArrayList<Rule>()

    fun submit(newItems: List<Rule>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rule, parent, false)
        return RuleHolder(view)
    }

    override fun onBindViewHolder(holder: RuleHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RuleHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvType: TextView = itemView.findViewById(R.id.tvRuleType)
        private val tvPattern: TextView = itemView.findViewById(R.id.tvRulePattern)
        private val switchEnabled: SwitchMaterial = itemView.findViewById(R.id.switchRuleEnabled)
        private val btnEdit: MaterialButton = itemView.findViewById(R.id.btnEditRule)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDeleteRule)

        fun bind(rule: Rule) {
            tvType.text = itemView.context.getString(rule.type.labelRes())
            tvPattern.text = rule.pattern

            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = rule.enabled
            switchEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(rule, checked)
            }
            btnEdit.setOnClickListener { onEdit(rule) }
            btnDelete.setOnClickListener { onDelete(rule) }
        }
    }
}
