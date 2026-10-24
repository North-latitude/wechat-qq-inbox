package com.local.unifiedinbox.collector

import android.app.Notification
import android.os.Build
import android.os.Bundle
import com.local.unifiedinbox.model.IncomingMessage
import com.local.unifiedinbox.model.MessageSource
import com.local.unifiedinbox.rules.RuleEngine

/**
 * Extracts messages from the platform notification extras without splitting or rewriting content.
 * We intentionally do not interpret "sender: content" strings: doing so corrupts message text.
 */
object NotificationParser {
    private val appDisplayNames = setOf("微信", "wechat", "qq")

    fun parse(
        notification: Notification,
        source: MessageSource,
        now: Long
    ): List<IncomingMessage> {
        val extras = notification.extras ?: return emptyList()
        val title = clean(extras.getCharSequence(Notification.EXTRA_TITLE))
        val conversationTitle =
            clean(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
        val senderExtra = clean(extras.getCharSequence(EXTRA_SENDER_KEY))
        val notificationTime = notification.`when`.takeIf { it > 0 } ?: now

        val messagingParts = extractMessagingParts(extras)
        if (messagingParts.isNotEmpty()) {
            val result = ArrayList<IncomingMessage>(messagingParts.size)
            for (part in messagingParts) {
                val content = clean(part.text)
                if (content.isEmpty()) continue
                val sender = part.sender.ifEmpty { senderExtra }
                val conversation = resolveConversation(conversationTitle, title, sender)
                    ?: continue
                result += IncomingMessage(
                    source = source,
                    conversation = conversation,
                    sender = sender,
                    content = content,
                    messageTimeMillis = if (part.time > 0) part.time else notificationTime
                )
            }
            if (result.isNotEmpty()) return result
        }

        val content = firstNonBlank(
            clean(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
            clean(extras.getCharSequence(Notification.EXTRA_TEXT)),
            cleanTextLines(extras),
            clean(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
            clean(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        ) ?: return emptyList()
        val conversation = resolveConversation(conversationTitle, title, senderExtra)
            ?: return emptyList()
        return listOf(
            IncomingMessage(
                source = source,
                conversation = conversation,
                sender = senderExtra,
                content = content,
                messageTimeMillis = notificationTime
            )
        )
    }

    private fun resolveConversation(
        conversationTitle: String,
        title: String,
        sender: String
    ): String? {
        for (candidate in listOf(conversationTitle, title, sender)) {
            if (candidate.isNotEmpty() && !isAppDisplayName(candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun isAppDisplayName(value: String): Boolean =
        appDisplayNames.contains(RuleEngine.normalizeConversation(value))

    private fun extractMessagingParts(extras: Bundle): List<MessagingPart> {
        val bundles = messageBundles(extras)
        if (bundles.isEmpty()) return emptyList()
        val result = ArrayList<MessagingPart>(bundles.size)
        for (bundle in bundles) {
            val text = clean(bundle.getCharSequence(KEY_TEXT))
            if (text.isEmpty()) continue
            result += MessagingPart(
                text = text,
                sender = extractBundleSender(bundle),
                time = bundle.getLong(KEY_TIME, 0L)
            )
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun messageBundles(extras: Bundle): List<Bundle> {
        val array = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (array != null) {
            return array.mapNotNull { it as? Bundle }
        }
        val raw = extras.get(Notification.EXTRA_MESSAGES)
        return when (raw) {
            is Array<*> -> raw.mapNotNull { it as? Bundle }
            is Iterable<*> -> raw.mapNotNull { it as? Bundle }
            else -> emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun extractBundleSender(bundle: Bundle): String {
        val raw = bundle.get(KEY_SENDER_PERSON) ?: bundle.get(KEY_SENDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val person = raw as? android.app.Person
            val name = clean(person?.name)
            if (name.isNotEmpty()) return name
        }
        return clean(raw as? CharSequence)
    }

    private fun cleanTextLines(extras: Bundle): String? {
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: return null
        val text = lines.mapNotNull { clean(it).ifEmpty { null } }
            .joinToString(separator = "\n")
        return text.ifEmpty { null }
    }

    private fun clean(value: CharSequence?): String =
        value?.toString()?.trim().orEmpty()

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private data class MessagingPart(
        val text: String,
        val sender: String,
        val time: Long
    )

    private const val KEY_TEXT = "text"
    private const val KEY_SENDER = "sender"
    private const val KEY_SENDER_PERSON = "sender_person"
    private const val KEY_TIME = "time"
    private const val EXTRA_SENDER_KEY = "android.sender"
}
