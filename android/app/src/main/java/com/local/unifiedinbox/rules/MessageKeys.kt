package com.local.unifiedinbox.rules

import java.security.MessageDigest
import java.util.Locale

/**
 * Stable identity for a notification message. The same notification update produces the same key,
 * so the durable dedup table can reject duplicate uploads.
 */
object MessageKeys {
    fun clientId(
        source: String,
        conversation: String,
        sender: String,
        content: String,
        messageTimeMillis: Long
    ): String {
        val raw = buildString {
            append(source.lowercase(Locale.ROOT))
            append('|')
            append(conversation)
            append('|')
            append(sender)
            append('|')
            append(content)
            append('|')
            append(messageTimeMillis)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
        }
    }
}
