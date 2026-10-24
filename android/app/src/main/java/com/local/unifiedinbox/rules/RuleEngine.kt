package com.local.unifiedinbox.rules

import com.local.unifiedinbox.model.Rule
import com.local.unifiedinbox.model.RuleType
import java.util.Locale

/**
 * Pure local rule evaluation. No Android framework dependency so it is unit-testable on the JVM.
 */
object RuleEngine {
    private val whitespace = Regex("\\s+")

    fun normalizeConversation(value: String): String =
        value.trim().replace(whitespace, " ").lowercase(Locale.ROOT)

    /**
     * Rules are ORed. Disabled rules are ignored. Results retain the input rule order.
     */
    fun evaluate(conversation: String, content: String, rules: List<Rule>): List<Rule> {
        val normalizedConversation = normalizeConversation(conversation)
        val normalizedContent = content.lowercase(Locale.ROOT)
        if (rules.isEmpty()) return emptyList()

        return rules.filter { rule ->
            if (!rule.enabled || rule.pattern.isBlank()) {
                false
            } else {
                when (rule.type) {
                    RuleType.GROUP, RuleType.CONTACT ->
                        normalizedConversation.isNotEmpty() &&
                            normalizeConversation(rule.pattern) == normalizedConversation

                    RuleType.KEYWORD ->
                        normalizedContent.contains(rule.pattern.lowercase(Locale.ROOT))
                }
            }
        }
    }
}
