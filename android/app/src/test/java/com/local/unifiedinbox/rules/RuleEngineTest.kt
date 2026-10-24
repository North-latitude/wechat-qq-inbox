package com.local.unifiedinbox.rules

import com.local.unifiedinbox.model.Rule
import com.local.unifiedinbox.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun rule(
        id: String,
        type: RuleType,
        pattern: String,
        enabled: Boolean = true
    ) = Rule(id = id, type = type, pattern = pattern, enabled = enabled)

    @Test
    fun groupRuleMatchesExactConversation() {
        val rules = listOf(rule("g1", RuleType.GROUP, "家人群"))
        val matched = RuleEngine.evaluate(
            conversation = "家人群",
            content = "周六回家吃饭吗？",
            rules = rules
        )
        assertEquals(listOf("g1"), matched.map { it.id })
    }

    @Test
    fun groupRuleIgnoresCaseAndCollapsesWhitespace() {
        val rules = listOf(rule("g1", RuleType.GROUP, "  Family   Group  "))
        val matched = RuleEngine.evaluate(
            conversation = " family group ",
            content = "anything",
            rules = rules
        )
        assertEquals(1, matched.size)
    }

    @Test
    fun groupRuleDoesNotMatchContentAsConversation() {
        val rules = listOf(rule("g1", RuleType.GROUP, "家人群"))
        val matched = RuleEngine.evaluate(
            conversation = "另一个群",
            content = "家人群 的消息",
            rules = rules
        )
        assertTrue(matched.isEmpty())
    }

    @Test
    fun contactRuleMatchesConversationNotMessageSender() {
        val rules = listOf(rule("c1", RuleType.CONTACT, "张三"))
        val groupMessage = RuleEngine.evaluate(
            conversation = "项目群",
            content = "张三：上线了",
            rules = rules
        )
        val directMessage = RuleEngine.evaluate(
            conversation = "张三",
            content = "上线了",
            rules = rules
        )
        assertTrue(groupMessage.isEmpty())
        assertEquals(listOf("c1"), directMessage.map { it.id })
    }

    @Test
    fun keywordRuleMatchesCaseInsensitiveSubstring() {
        val rules = listOf(rule("k1", RuleType.KEYWORD, "Deploy"))
        val matched = RuleEngine.evaluate(
            conversation = "项目群",
            content = "please DEPLOY the service",
            rules = rules
        )
        assertEquals(listOf("k1"), matched.map { it.id })
    }

    @Test
    fun disabledRulesAreIgnored() {
        val rules = listOf(
            rule("g1", RuleType.GROUP, "家人群", enabled = false),
            rule("k1", RuleType.KEYWORD, "回家", enabled = true)
        )
        val matched = RuleEngine.evaluate(
            conversation = "家人群",
            content = "周六回家吃饭吗？",
            rules = rules
        )
        assertEquals(listOf("k1"), matched.map { it.id })
    }

    @Test
    fun ruleTypesAreOred() {
        val rules = listOf(
            rule("g1", RuleType.GROUP, "家人群"),
            rule("k1", RuleType.KEYWORD, "回家"),
            rule("c1", RuleType.CONTACT, "李四")
        )
        val matched = RuleEngine.evaluate(
            conversation = "家人群",
            content = "周六回家吃饭吗？",
            rules = rules
        )
        assertEquals(listOf("g1", "k1"), matched.map { it.id })
    }

    @Test
    fun emptyRulesNeverMatch() {
        val matched = RuleEngine.evaluate(
            conversation = "家人群",
            content = "周六回家吃饭吗？",
            rules = emptyList()
        )
        assertTrue(matched.isEmpty())
    }

    @Test
    fun matchedRulesRetainInputOrdering() {
        val rules = listOf(
            rule("k1", RuleType.KEYWORD, "回"),
            rule("g1", RuleType.GROUP, "家人群"),
            rule("k2", RuleType.KEYWORD, "回家"),
            rule("c1", RuleType.CONTACT, "家人群")
        )
        val matched = RuleEngine.evaluate(
            conversation = "家人群",
            content = "周六回家吃饭吗？",
            rules = rules
        )
        assertEquals(listOf("k1", "g1", "k2", "c1"), matched.map { it.id })
    }

    @Test
    fun emptyConversationCannotMatchGroupOrContact() {
        val rules = listOf(
            rule("g1", RuleType.GROUP, "家人群"),
            rule("c1", RuleType.CONTACT, "张三")
        )
        val matched = RuleEngine.evaluate(
            conversation = "   ",
            content = "家人群 张三",
            rules = rules
        )
        assertTrue(matched.isEmpty())
    }
}
