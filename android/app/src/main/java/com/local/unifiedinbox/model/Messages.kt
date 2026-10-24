package com.local.unifiedinbox.model

enum class MessageSource(val packageName: String, val apiValue: String) {
    WECHAT("com.tencent.mm", "wechat"),
    QQ("com.tencent.mobileqq", "qq");

    companion object {
        fun fromPackage(packageName: String?): MessageSource? =
            entries.firstOrNull { it.packageName == packageName }

        fun fromApiValue(value: String?): MessageSource? =
            entries.firstOrNull { it.apiValue == value }
    }
}

/**
 * A notification-derived message before rule evaluation. It exists only in memory.
 */
data class IncomingMessage(
    val source: MessageSource,
    val conversation: String,
    val sender: String,
    val content: String,
    val messageTimeMillis: Long
)

/**
 * A rule-matched message that may be persisted to the upload outbox.
 */
data class OutboxMessage(
    val clientId: String,
    val source: MessageSource,
    val conversation: String,
    val sender: String,
    val content: String,
    val messageTimeMillis: Long,
    val matchedRules: List<MatchedRule>
)

data class OutboxItem(
    val id: Long,
    val clientId: String,
    val payload: String,
    val attempts: Int
)

data class OutboxCounts(
    val pending: Long,
    val dead: Long
)

data class HitLogEntry(
    val id: Long,
    val clientId: String,
    val conversation: String,
    val rules: String,
    val createdAt: Long
)

data class DailyCounters(
    val notificationsSeen: Long = 0,
    val matched: Long = 0,
    val skippedNoRule: Long = 0,
    val duplicates: Long = 0,
    val parseErrors: Long = 0,
    val summarySkipped: Long = 0
)

data class InboxMessage(
    val id: Long,
    val source: MessageSource,
    val conversation: String,
    val sender: String?,
    val content: String,
    val messageTimeMillis: Long,
    val matchedRules: List<MatchedRule>,
    val isRead: Boolean
)

data class InboxConversation(
    val source: MessageSource,
    val conversation: String,
    val unreadCount: Long,
    val lastMessageAt: Long
)

data class InboxConversationSummary(
    val source: MessageSource,
    val conversation: String,
    val unreadCount: Long,
    val lastMessageAt: Long,
    val lastContent: String,
    val lastSender: String?
)

data class InboxCounts(
    val totalUnread: Long,
    val todayUnread: Long,
    val totalCount: Long,
    val todayCount: Long
)

enum class MetricType(val suffix: String) {
    NOTIFICATIONS_SEEN("notifications_seen"),
    MATCHED("matched"),
    SKIPPED_NO_RULE("skipped_no_rule"),
    DUPLICATES("duplicates"),
    PARSE_ERRORS("parse_errors"),
    SUMMARY_SKIPPED("summary_skipped")
}

data class EnqueueResult(
    val inserted: Int,
    val duplicates: Int
)
