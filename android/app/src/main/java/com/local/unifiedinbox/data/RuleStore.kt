package com.local.unifiedinbox.data

import android.content.Context
import com.local.unifiedinbox.model.Rule
import com.local.unifiedinbox.model.RuleType
import com.local.unifiedinbox.rules.RuleEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RuleMutationResult {
    OK,
    INVALID_PATTERN,
    DUPLICATE_ACTIVE,
    NOT_FOUND
}

class RuleStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    init {
        synchronized(lock) {
            if (!prefs.contains(KEY_RULES)) {
                prefs.edit().putString(KEY_RULES, "[]").apply()
            }
        }
    }

    fun all(): List<Rule> = synchronized(lock) {
        loadLocked()
    }

    fun add(type: RuleType, pattern: String, enabled: Boolean): RuleMutationResult {
        val trimmed = pattern.trim()
        if (!isValidPattern(trimmed)) return RuleMutationResult.INVALID_PATTERN
        return synchronized(lock) {
            val rules = loadLocked()
            val duplicate = enabled && rules.any {
                it.enabled && it.type == type && samePattern(it.pattern, trimmed)
            }
            if (duplicate) {
                RuleMutationResult.DUPLICATE_ACTIVE
            } else {
                val updated = rules + Rule(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    pattern = trimmed,
                    enabled = enabled
                )
                saveLocked(updated)
                RuleMutationResult.OK
            }
        }
    }

    fun update(
        id: String,
        type: RuleType,
        pattern: String,
        enabled: Boolean
    ): RuleMutationResult {
        val trimmed = pattern.trim()
        if (!isValidPattern(trimmed)) return RuleMutationResult.INVALID_PATTERN
        return synchronized(lock) {
            val rules = loadLocked()
            val index = rules.indexOfFirst { it.id == id }
            when {
                index < 0 -> RuleMutationResult.NOT_FOUND
                enabled && rules.any {
                    it.enabled && it.id != id && it.type == type &&
                        samePattern(it.pattern, trimmed)
                } -> RuleMutationResult.DUPLICATE_ACTIVE
                else -> {
                    val updated = rules.toMutableList()
                    updated[index] = Rule(
                        id = id,
                        type = type,
                        pattern = trimmed,
                        enabled = enabled
                    )
                    saveLocked(updated)
                    RuleMutationResult.OK
                }
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean): RuleMutationResult {
        return synchronized(lock) {
            val rules = loadLocked()
            val index = rules.indexOfFirst { it.id == id }
            if (index < 0) {
                RuleMutationResult.NOT_FOUND
            } else {
                val target = rules[index]
                val duplicate = enabled && !target.enabled && rules.any {
                    it.enabled && it.id != id && it.type == target.type &&
                        samePattern(it.pattern, target.pattern)
                }
                if (duplicate) {
                    RuleMutationResult.DUPLICATE_ACTIVE
                } else {
                    val updated = rules.toMutableList()
                    updated[index] = target.copy(enabled = enabled)
                    saveLocked(updated)
                    RuleMutationResult.OK
                }
            }
        }
    }

    fun delete(id: String): Boolean {
        return synchronized(lock) {
            val rules = loadLocked()
            val updated = rules.filterNot { it.id == id }
            if (updated.size == rules.size) {
                false
            } else {
                saveLocked(updated)
                true
            }
        }
    }

    fun hasActiveDuplicate(
        type: RuleType,
        pattern: String,
        excludeId: String? = null
    ): Boolean = synchronized(lock) {
        loadLocked().any {
            it.enabled && it.id != excludeId && it.type == type && samePattern(it.pattern, pattern)
        }
    }

    fun isValidPattern(pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return false
        val length = trimmed.codePointCount(0, trimmed.length)
        return length in 1..MAX_PATTERN_LENGTH
    }

    private fun samePattern(left: String, right: String): Boolean =
        RuleEngine.normalizeConversation(left) == RuleEngine.normalizeConversation(right)

    private fun loadLocked(): List<Rule> {
        val raw = prefs.getString(KEY_RULES, "[]") ?: "[]"
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        val result = ArrayList<Rule>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id", "").trim()
            val type = RuleType.fromStorage(item.optString("type", ""))
            val pattern = item.optString("pattern", "").trim()
            if (id.isEmpty() || type == null || pattern.isEmpty()) continue
            if (pattern.codePointCount(0, pattern.length) > MAX_PATTERN_LENGTH) continue
            result += Rule(
                id = id,
                type = type,
                pattern = pattern,
                enabled = item.optBoolean("enabled", true)
            )
        }
        return result
    }

    private fun saveLocked(rules: List<Rule>) {
        val array = JSONArray()
        for (rule in rules) {
            array.put(
                JSONObject().apply {
                    put("id", rule.id)
                    put("type", rule.type.apiValue)
                    put("pattern", rule.pattern)
                    put("enabled", rule.enabled)
                }
            )
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "inbox_rules"
        private const val KEY_RULES = "rules"
        private const val MAX_PATTERN_LENGTH = 100
    }
}
