package com.local.unifiedinbox.model

enum class RuleType(val apiValue: String) {
    GROUP("group"),
    CONTACT("contact"),
    KEYWORD("keyword");

    companion object {
        fun fromStorage(value: String?): RuleType? {
            if (value == null) return null
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                    it.apiValue.equals(value, ignoreCase = true)
            }
        }
    }
}

data class Rule(
    val id: String,
    val type: RuleType,
    val pattern: String,
    val enabled: Boolean
)

data class MatchedRule(
    val id: String,
    val type: RuleType,
    val pattern: String
)
