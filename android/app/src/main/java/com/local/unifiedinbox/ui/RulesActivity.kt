package com.local.unifiedinbox.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.local.unifiedinbox.AppGraph
import com.local.unifiedinbox.R
import com.local.unifiedinbox.data.RuleMutationResult
import com.local.unifiedinbox.model.Rule
import com.local.unifiedinbox.model.RuleType

class RulesActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: RuleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        AppGraph.initialize(this)
        setContentView(R.layout.activity_rules)

        recycler = findViewById(R.id.recyclerRules)
        emptyView = findViewById(R.id.tvRulesEmpty)
        adapter = RuleAdapter(
            onToggle = ::toggleRule,
            onEdit = ::showRuleDialog,
            onDelete = ::confirmDelete
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<MaterialButton>(R.id.btnAddRule).setOnClickListener {
            showRuleDialog(null)
        }
    }

    override fun onResume() {
        super.onResume()
        loadRules()
    }

    private fun loadRules() {
        val rules = AppGraph.ruleStore.all()
        adapter.submit(rules)
        emptyView.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleRule(rule: Rule, enabled: Boolean) {
        when (AppGraph.ruleStore.setEnabled(rule.id, enabled)) {
            RuleMutationResult.OK -> {
                loadRules()
                AppGraph.heartbeatManager.onRulesChanged()
            }

            RuleMutationResult.DUPLICATE_ACTIVE -> {
                Toast.makeText(this, R.string.rules_toggle_duplicate, Toast.LENGTH_LONG).show()
                loadRules()
            }

            RuleMutationResult.INVALID_PATTERN,
            RuleMutationResult.NOT_FOUND -> loadRules()
        }
    }

    private fun showRuleDialog(existing: Rule?) {
        val view = layoutInflater.inflate(R.layout.dialog_rule, null)
        val spinner = view.findViewById<Spinner>(R.id.spinnerRuleType)
        val patternInput = view.findViewById<TextInputEditText>(R.id.etRulePattern)
        val enabledSwitch = view.findViewById<SwitchMaterial>(R.id.switchRuleDialogEnabled)
        val types = RuleType.entries.toList()
        val labels = types.map { getString(it.labelRes()) }
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        if (existing != null) {
            spinner.setSelection(types.indexOf(existing.type).coerceAtLeast(0))
            patternInput.setText(existing.pattern)
            enabledSwitch.isChecked = existing.enabled
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(
                if (existing == null) {
                    R.string.rules_dialog_add_title
                } else {
                    R.string.rules_dialog_edit_title
                }
            )
            .setView(view)
            .setNegativeButton(R.string.rules_cancel, null)
            .setPositiveButton(R.string.rules_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pattern = patternInput.text?.toString().orEmpty()
                if (!AppGraph.ruleStore.isValidPattern(pattern)) {
                    patternInput.error = getString(R.string.rules_pattern_required)
                    return@setOnClickListener
                }
                val type = types[spinner.selectedItemPosition]
                val enabled = enabledSwitch.isChecked
                val result = if (existing == null) {
                    AppGraph.ruleStore.add(type, pattern, enabled)
                } else {
                    AppGraph.ruleStore.update(existing.id, type, pattern, enabled)
                }

                when (result) {
                    RuleMutationResult.OK -> {
                        dialog.dismiss()
                        loadRules()
                        AppGraph.heartbeatManager.onRulesChanged()
                        Toast.makeText(this, R.string.rules_rule_saved, Toast.LENGTH_SHORT).show()
                    }

                    RuleMutationResult.DUPLICATE_ACTIVE -> {
                        patternInput.error = getString(R.string.rules_duplicate_active)
                    }

                    RuleMutationResult.INVALID_PATTERN -> {
                        patternInput.error = getString(R.string.rules_pattern_required)
                    }

                    RuleMutationResult.NOT_FOUND -> {
                        dialog.dismiss()
                        loadRules()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(rule: Rule) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rules_delete_title)
            .setMessage(R.string.rules_delete_message)
            .setNegativeButton(R.string.rules_cancel, null)
            .setPositiveButton(R.string.rules_delete) { _, _ ->
                if (AppGraph.ruleStore.delete(rule.id)) {
                    loadRules()
                    AppGraph.heartbeatManager.onRulesChanged()
                    Toast.makeText(this, R.string.rules_deleted, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
