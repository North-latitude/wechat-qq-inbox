package com.local.unifiedinbox.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageKeysTest {

    @Test
    fun identicalInputsProduceIdenticalKeys() {
        val first = MessageKeys.clientId(
            source = "wechat",
            conversation = "家人群",
            sender = "妈妈",
            content = "周六回家吃饭吗？",
            messageTimeMillis = 1761105570000L
        )
        val second = MessageKeys.clientId(
            source = "wechat",
            conversation = "家人群",
            sender = "妈妈",
            content = "周六回家吃饭吗？",
            messageTimeMillis = 1761105570000L
        )
        assertEquals(first, second)
    }

    @Test
    fun keyIsStableForUnicodeInput() {
        val key = MessageKeys.clientId(
            source = "wechat",
            conversation = "家人群",
            sender = "妈妈",
            content = "周六回家吃饭吗？",
            messageTimeMillis = 1761105570000L
        )
        assertEquals(
            "abe7af0d0f90045bce312dd23c8bdef1c2e16ee465d635ba073b5f367e4a4f32",
            key
        )
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun changingContentChangesKey() {
        val first = MessageKeys.clientId(
            source = "wechat",
            conversation = "家人群",
            sender = "妈妈",
            content = "周六回家吃饭吗？",
            messageTimeMillis = 1761105570000L
        )
        val second = MessageKeys.clientId(
            source = "wechat",
            conversation = "家人群",
            sender = "妈妈",
            content = "周日回家吃饭吗？",
            messageTimeMillis = 1761105570000L
        )
        assertNotEquals(first, second)
    }

    @Test
    fun changingTimeChangesKey() {
        val first = MessageKeys.clientId(
            source = "qq",
            conversation = "项目群",
            sender = "李四",
            content = "上线了",
            messageTimeMillis = 1761105570000L
        )
        val second = MessageKeys.clientId(
            source = "qq",
            conversation = "项目群",
            sender = "李四",
            content = "上线了",
            messageTimeMillis = 1761105570001L
        )
        assertNotEquals(first, second)
    }

    @Test
    fun sourceIsNormalizedToLowercase() {
        val upper = MessageKeys.clientId(
            source = "WECHAT",
            conversation = "家人群",
            sender = "妈妈",
            content = "周六回家吃饭吗？",
            messageTimeMillis = 1761105570000L
        )
        val lower = MessageKeys.clientId(
            source = "wechat",
            conversation = "家人群",
            sender = "妈妈",
            content = "周六回家吃饭吗？",
            messageTimeMillis = 1761105570000L
        )
        assertEquals(lower, upper)
    }
}
