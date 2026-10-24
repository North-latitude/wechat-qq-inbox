package com.local.unifiedinbox.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.local.unifiedinbox.R
import com.local.unifiedinbox.model.EnqueueResult
import com.local.unifiedinbox.model.HitLogEntry
import com.local.unifiedinbox.model.InboxConversation
import com.local.unifiedinbox.model.InboxConversationSummary
import com.local.unifiedinbox.model.InboxCounts
import com.local.unifiedinbox.model.InboxMessage
import com.local.unifiedinbox.model.MatchedRule
import com.local.unifiedinbox.model.MessageSource
import com.local.unifiedinbox.model.MetricType
import com.local.unifiedinbox.model.OutboxCounts
import com.local.unifiedinbox.model.OutboxItem
import com.local.unifiedinbox.model.OutboxMessage
import com.local.unifiedinbox.model.RuleType
import com.local.unifiedinbox.util.TimeFormat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class CollectorRepository(
    context: Context,
    private val database: InboxDatabase,
    private val metrics: MetricsStore
) {
    private val appContext = context.applicationContext

    /**
     * Persists only rule-matched messages. The local inbox row is always written;
     * the upload outbox is written only when [queueForUpload] is true (PC sync on).
     * Dedup, inbox, optional outbox, hit log and the matched counter are committed
     * in one SQLite transaction. An empty matched_rules list is refused.
     */
    fun enqueueMatched(
        messages: List<OutboxMessage>,
        now: Long,
        queueForUpload: Boolean
    ): EnqueueResult {
        if (messages.isEmpty()) return EnqueueResult(0, 0)
        return database.transaction { db ->
            var inserted = 0
            var duplicates = 0
            for (message in messages) {
                if (message.matchedRules.isEmpty()) continue

                if (dedupExists(db, message.clientId)) {
                    metrics.incrementLocked(db, MetricType.DUPLICATES, now)
                    duplicates++
                    continue
                }

                val rulesJson = buildRulesJson(message.matchedRules)
                db.insertOrThrow(
                    "messages",
                    null,
                    ContentValues().apply {
                        put("client_id", message.clientId)
                        put("source", message.source.apiValue)
                        put("conversation", message.conversation)
                        put("sender", message.sender)
                        put("content", message.content)
                        put("message_time", message.messageTimeMillis)
                        put("matched_rules", rulesJson)
                        put("is_read", 0)
                        put("created_at", now)
                    }
                )
                db.insertOrThrow(
                    "dedup",
                    null,
                    ContentValues().apply {
                        put("key", message.clientId)
                        put("created_at", now)
                    }
                )
                if (queueForUpload) {
                    db.insertOrThrow(
                        "outbox",
                        null,
                        ContentValues().apply {
                            put("client_id", message.clientId)
                            put("payload", buildPayload(message).toString())
                            put("created_at", now)
                            put("attempts", 0)
                            put("state", STATE_PENDING)
                        }
                    )
                }
                db.insertOrThrow(
                    "hit_log",
                    null,
                    ContentValues().apply {
                        put("client_id", message.clientId)
                        put("conversation", message.conversation)
                        put("rules", formatRules(message.matchedRules))
                        put("created_at", now)
                    }
                )
                metrics.incrementLocked(db, MetricType.MATCHED, now)
                inserted++
            }
            pruneLocked(db, now)
            EnqueueResult(inserted = inserted, duplicates = duplicates)
        }
    }

    fun inboxMessages(
        source: String? = null,
        conversation: String? = null,
        search: String? = null,
        unreadOnly: Boolean = false,
        limit: Int = DEFAULT_INBOX_LIMIT
    ): List<InboxMessage> {
        val (clauses, args) = inboxFilters(source, conversation, search, unreadOnly)
        val where = if (clauses.isEmpty()) "" else "WHERE " + clauses.joinToString(" AND ")
        val safeLimit = limit.coerceIn(1, MAX_INBOX_LIMIT)
        return database.read { db ->
            val result = ArrayList<InboxMessage>()
            db.rawQuery(
                "SELECT id, source, conversation, sender, content, message_time, " +
                    "matched_rules, is_read FROM messages $where " +
                    "ORDER BY message_time DESC, id DESC LIMIT ?",
                (args + safeLimit.toString()).toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val sourceValue = MessageSource.fromApiValue(cursor.getString(1)) ?: continue
                    result += InboxMessage(
                        id = cursor.getLong(0),
                        source = sourceValue,
                        conversation = cursor.getString(2).orEmpty(),
                        sender = cursor.getString(3),
                        content = cursor.getString(4).orEmpty(),
                        messageTimeMillis = cursor.getLong(5),
                        matchedRules = parseRules(cursor.getString(6).orEmpty()),
                        isRead = cursor.getInt(7) != 0
                    )
                }
            }
            result
        }
    }

    fun inboxConversations(source: String? = null): List<InboxConversation> {
        val args = ArrayList<String>()
        var where = ""
        if (!source.isNullOrBlank()) {
            where = "WHERE source = ?"
            args += source
        }
        return database.read { db ->
            val result = ArrayList<InboxConversation>()
            db.rawQuery(
                "SELECT source, conversation, " +
                    "SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END) AS unread_count, " +
                    "MAX(message_time) AS last_at FROM messages $where " +
                    "GROUP BY source, conversation ORDER BY last_at DESC",
                args.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val sourceValue = MessageSource.fromApiValue(cursor.getString(0)) ?: continue
                    result += InboxConversation(
                        source = sourceValue,
                        conversation = cursor.getString(1).orEmpty(),
                        unreadCount = cursor.getLong(2),
                        lastMessageAt = cursor.getLong(3)
                    )
                }
            }
            result
        }
    }

    fun inboxCounts(): InboxCounts = database.read { db ->
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        db.rawQuery(
            "SELECT " +
                "SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN is_read = 0 AND message_time >= ? THEN 1 ELSE 0 END), " +
                "COUNT(*), " +
                "SUM(CASE WHEN message_time >= ? THEN 1 ELSE 0 END) FROM messages",
            arrayOf(todayStart.toString(), todayStart.toString())
        ).use { cursor ->
            cursor.moveToFirst()
            InboxCounts(
                totalUnread = longOrZero(cursor, 0),
                todayUnread = longOrZero(cursor, 1),
                totalCount = longOrZero(cursor, 2),
                todayCount = longOrZero(cursor, 3)
            )
        }
    }

    fun conversationSummaries(
        source: String? = null,
        search: String? = null,
        unreadOnly: Boolean = false
    ): List<InboxConversationSummary> {
        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        if (!source.isNullOrBlank()) {
            clauses += "m.source = ?"
            args += source
        }
        val query = search?.trim().orEmpty()
        if (query.isNotEmpty()) {
            val escaped = query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            val pattern = "%$escaped%"
            clauses += "EXISTS (SELECT 1 FROM messages x " +
                "WHERE x.source = m.source AND x.conversation = m.conversation " +
                "AND (x.content LIKE ? ESCAPE '\\' OR x.conversation LIKE ? ESCAPE '\\' " +
                "OR IFNULL(x.sender, '') LIKE ? ESCAPE '\\'))"
            args += pattern
            args += pattern
            args += pattern
        }
        val where = if (clauses.isEmpty()) "" else "WHERE " + clauses.joinToString(" AND ")
        val having = if (unreadOnly) {
            "HAVING SUM(CASE WHEN m.is_read = 0 THEN 1 ELSE 0 END) > 0"
        } else {
            ""
        }
        return database.read { db ->
            val result = ArrayList<InboxConversationSummary>()
            db.rawQuery(
                "SELECT m.source, m.conversation, " +
                    "SUM(CASE WHEN m.is_read = 0 THEN 1 ELSE 0 END) AS unread_count, " +
                    "MAX(m.message_time) AS last_at, " +
                    "(SELECT x.content FROM messages x " +
                    " WHERE x.source = m.source AND x.conversation = m.conversation " +
                    " ORDER BY x.message_time DESC, x.id DESC LIMIT 1), " +
                    "(SELECT x.sender FROM messages x " +
                    " WHERE x.source = m.source AND x.conversation = m.conversation " +
                    " ORDER BY x.message_time DESC, x.id DESC LIMIT 1) " +
                    "FROM messages m $where " +
                    "GROUP BY m.source, m.conversation $having " +
                    "ORDER BY last_at DESC",
                args.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val sourceValue = MessageSource.fromApiValue(cursor.getString(0)) ?: continue
                    result += InboxConversationSummary(
                        source = sourceValue,
                        conversation = cursor.getString(1).orEmpty(),
                        unreadCount = longOrZero(cursor, 2),
                        lastMessageAt = longOrZero(cursor, 3),
                        lastContent = cursor.getString(4).orEmpty(),
                        lastSender = cursor.getString(5)
                    )
                }
            }
            result
        }
    }

    fun markConversationRead(source: String, conversation: String): Int = database.transaction { db ->
        db.update(
            "messages",
            ContentValues().apply { put("is_read", 1) },
            "source = ? AND conversation = ? AND is_read = 0",
            arrayOf(source, conversation)
        )
    }

    fun deleteInboxMessages(ids: Set<Long>): Int {
        if (ids.isEmpty()) return 0
        return database.transaction { db ->
            var deleted = 0
            ids.chunked(500).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                deleted += db.delete(
                    "messages",
                    "id IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray()
                )
            }
            deleted
        }
    }

    fun deleteConversation(source: String, conversation: String): Int = database.transaction { db ->
        db.delete(
            "messages",
            "source = ? AND conversation = ?",
            arrayOf(source, conversation)
        )
    }

    private fun longOrZero(cursor: android.database.Cursor, index: Int): Long =
        if (cursor.isNull(index)) 0L else cursor.getLong(index)

    fun markInboxRead(messageId: Long): Boolean = database.transaction { db ->
        db.update(
            "messages",
            ContentValues().apply { put("is_read", 1) },
            "id = ?",
            arrayOf(messageId.toString())
        ) > 0
    }

    fun markAllInboxRead(
        source: String? = null,
        conversation: String? = null,
        search: String? = null
    ): Int {
        val (clauses, args) = inboxFilters(source, conversation, search, unreadOnly = true)
        val where = if (clauses.isEmpty()) "WHERE is_read = 0" else
            "WHERE " + clauses.joinToString(" AND ")
        return database.transaction { db ->
            db.update(
                "messages",
                ContentValues().apply { put("is_read", 1) },
                where.removePrefix("WHERE "),
                args.toTypedArray()
            )
        }
    }

    fun fetchPending(limit: Int = MAX_BATCH_SIZE): List<OutboxItem> {
        if (limit <= 0) return emptyList()
        val safeLimit = limit.coerceAtMost(MAX_BATCH_SIZE)
        return database.read { db ->
            val result = ArrayList<OutboxItem>(safeLimit)
            db.rawQuery(
                "SELECT id, client_id, payload, attempts FROM outbox " +
                    "WHERE state = ? ORDER BY id ASC LIMIT ?",
                arrayOf(STATE_PENDING, safeLimit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result += OutboxItem(
                        id = cursor.getLong(0),
                        clientId = cursor.getString(1),
                        payload = cursor.getString(2),
                        attempts = cursor.getInt(3)
                    )
                }
            }
            result
        }
    }

    fun pendingCount(): Long = database.read { db ->
        DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM outbox WHERE state = ?",
            arrayOf(STATE_PENDING)
        )
    }

    fun outboxCounts(): OutboxCounts = database.read { db ->
        val pending = DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM outbox WHERE state = ?",
            arrayOf(STATE_PENDING)
        )
        val dead = DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM outbox WHERE state = ?",
            arrayOf(STATE_DEAD)
        )
        OutboxCounts(pending = pending, dead = dead)
    }

    fun markUploaded(clientIds: List<String>) {
        if (clientIds.isEmpty()) return
        database.transaction { db ->
            db.delete("outbox", clientIdInClause(clientIds.size), clientIds.toTypedArray())
        }
    }

    fun markDead(clientIds: List<String>, error: String) {
        if (clientIds.isEmpty()) return
        val safeError = sanitizeError(error)
        val args = mutableListOf<Any?>(STATE_DEAD, safeError, STATE_PENDING)
        args.addAll(clientIds)
        database.transaction { db ->
            db.execSQL(
                "UPDATE outbox SET state = ?, attempts = attempts + 1, last_error = ? " +
                    "WHERE state = ? AND client_id IN (${placeholders(clientIds.size)})",
                args.toTypedArray()
            )
        }
    }

    fun markPendingFailure(clientIds: List<String>, error: String) {
        if (clientIds.isEmpty()) return
        val safeError = sanitizeError(error)
        val args = mutableListOf<Any?>(safeError, STATE_PENDING)
        args.addAll(clientIds)
        database.transaction { db ->
            db.execSQL(
                "UPDATE outbox SET attempts = attempts + 1, last_error = ? " +
                    "WHERE state = ? AND client_id IN (${placeholders(clientIds.size)})",
                args.toTypedArray()
            )
        }
    }

    fun hitLog(limit: Int = HIT_LOG_LIMIT): List<HitLogEntry> {
        val safeLimit = limit.coerceIn(1, HIT_LOG_LIMIT)
        return database.read { db ->
            val result = ArrayList<HitLogEntry>(safeLimit)
            db.rawQuery(
                "SELECT id, client_id, conversation, rules, created_at FROM hit_log " +
                    "ORDER BY created_at DESC, id DESC LIMIT ?",
                arrayOf(safeLimit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result += HitLogEntry(
                        id = cursor.getLong(0),
                        clientId = cursor.getString(1).orEmpty(),
                        conversation = cursor.getString(2).orEmpty(),
                        rules = cursor.getString(3).orEmpty(),
                        createdAt = cursor.getLong(4)
                    )
                }
            }
            result
        }
    }

    fun prune(now: Long = System.currentTimeMillis()) {
        database.transaction { db ->
            pruneLocked(db, now)
        }
    }

    private fun dedupExists(db: SQLiteDatabase, clientId: String): Boolean =
        DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM dedup WHERE key = ?",
            arrayOf(clientId)
        ) > 0

    private fun pruneLocked(db: SQLiteDatabase, now: Long) {
        val dedupCutoff = now - TimeUnit.DAYS.toMillis(DEDUP_RETENTION_DAYS)
        val hitLogCutoff = now - TimeUnit.DAYS.toMillis(HIT_LOG_RETENTION_DAYS)
        db.delete("dedup", "created_at < ?", arrayOf(dedupCutoff.toString()))
        db.delete("hit_log", "created_at < ?", arrayOf(hitLogCutoff.toString()))
    }

    private fun buildRulesJson(rules: List<MatchedRule>): String {
        val array = JSONArray()
        for (rule in rules) {
            array.put(
                JSONObject().apply {
                    put("id", rule.id)
                    put("type", rule.type.apiValue)
                    put("pattern", rule.pattern)
                }
            )
        }
        return array.toString()
    }

    private fun parseRules(raw: String): List<MatchedRule> {
        if (raw.isBlank()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        val result = ArrayList<MatchedRule>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id", "").trim()
            val type = RuleType.fromStorage(item.optString("type", "")) ?: continue
            val pattern = item.optString("pattern", "").trim()
            if (id.isNotEmpty() && pattern.isNotEmpty()) {
                result += MatchedRule(id = id, type = type, pattern = pattern)
            }
        }
        return result
    }

    private fun inboxFilters(
        source: String?,
        conversation: String?,
        search: String?,
        unreadOnly: Boolean
    ): Pair<List<String>, MutableList<String>> {
        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        if (!source.isNullOrBlank()) {
            clauses += "source = ?"
            args += source
        }
        if (!conversation.isNullOrBlank()) {
            clauses += "conversation = ?"
            args += conversation
        }
        val query = search?.trim().orEmpty()
        if (query.isNotEmpty()) {
            val escaped = query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            val pattern = "%$escaped%"
            clauses += "(content LIKE ? ESCAPE '\\' OR conversation LIKE ? ESCAPE '\\' " +
                "OR IFNULL(sender, '') LIKE ? ESCAPE '\\')"
            args += pattern
            args += pattern
            args += pattern
        }
        if (unreadOnly) clauses += "is_read = 0"
        return clauses to args
    }

    private fun buildPayload(message: OutboxMessage): JSONObject {
        return JSONObject().apply {
            put("client_id", message.clientId)
            put("source", message.source.apiValue)
            put("conversation", message.conversation)
            put("sender", message.sender)
            put("content", message.content)
            put("message_time", TimeFormat.isoUtc(message.messageTimeMillis))
            put("matched_rules", JSONArray(buildRulesJson(message.matchedRules)))
        }
    }

    private fun formatRules(rules: List<MatchedRule>): String =
        rules.joinToString(separator = "；") { rule ->
            "${ruleTypeLabel(rule.type)}「${rule.pattern}」"
        }

    private fun ruleTypeLabel(type: RuleType): String = appContext.getString(
        when (type) {
            RuleType.GROUP -> R.string.hit_log_rule_group
            RuleType.CONTACT -> R.string.hit_log_rule_contact
            RuleType.KEYWORD -> R.string.hit_log_rule_keyword
        }
    )

    private fun sanitizeError(error: String): String =
        error.replace('\n', ' ').replace('\r', ' ').trim().take(MAX_ERROR_LENGTH)

    private fun clientIdInClause(count: Int): String =
        "client_id IN (${placeholders(count)})"

    private fun placeholders(count: Int): String =
        List(count) { "?" }.joinToString(separator = ",")

    companion object {
        const val MAX_BATCH_SIZE = 50
        const val HIT_LOG_LIMIT = 50
        const val DEFAULT_INBOX_LIMIT = 300
        const val MAX_INBOX_LIMIT = 1000
        private const val STATE_PENDING = "pending"
        private const val STATE_DEAD = "dead"
        private const val DEDUP_RETENTION_DAYS = 7L
        private const val HIT_LOG_RETENTION_DAYS = 30L
        private const val MAX_ERROR_LENGTH = 300
    }
}
