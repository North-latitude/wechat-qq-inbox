package com.local.unifiedinbox.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.local.unifiedinbox.model.DailyCounters
import com.local.unifiedinbox.model.MetricType
import java.time.Instant
import java.time.ZoneId

/**
 * Daily counters and the persisted last-notification timestamp. All access is serialized by
 * InboxDatabase's lock, so callbacks from the listener and WorkManager cannot race.
 */
class MetricsStore(private val database: InboxDatabase) {

    fun increment(type: MetricType, amount: Long = 1, atMillis: Long = System.currentTimeMillis()) {
        database.transaction { db ->
            incrementLocked(db, type, atMillis, amount)
        }
    }

    fun incrementAll(
        counts: Map<MetricType, Long>,
        atMillis: Long = System.currentTimeMillis()
    ) {
        if (counts.isEmpty()) return
        database.transaction { db ->
            for ((type, amount) in counts) {
                if (amount > 0) incrementLocked(db, type, atMillis, amount)
            }
        }
    }

    internal fun incrementLocked(
        db: SQLiteDatabase,
        type: MetricType,
        atMillis: Long,
        amount: Long = 1
    ) {
        if (amount <= 0) return
        val key = dailyKey(type, atMillis)
        val current = readLong(db, key)
        val values = ContentValues().apply {
            put("key", key)
            put("value", current + amount)
        }
        db.insertWithOnConflict("metrics", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun snapshot(atMillis: Long = System.currentTimeMillis()): DailyCounters = database.read { db ->
        val prefix = localDate(atMillis) + ":"
        val values = HashMap<String, Long>()
        db.rawQuery(
            "SELECT key, value FROM metrics WHERE key LIKE ?",
            arrayOf("$prefix%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                values[cursor.getString(0)] = cursor.getLong(1)
            }
        }
        DailyCounters(
            notificationsSeen = values[dailyKey(MetricType.NOTIFICATIONS_SEEN, atMillis)] ?: 0,
            matched = values[dailyKey(MetricType.MATCHED, atMillis)] ?: 0,
            skippedNoRule = values[dailyKey(MetricType.SKIPPED_NO_RULE, atMillis)] ?: 0,
            duplicates = values[dailyKey(MetricType.DUPLICATES, atMillis)] ?: 0,
            parseErrors = values[dailyKey(MetricType.PARSE_ERRORS, atMillis)] ?: 0,
            summarySkipped = values[dailyKey(MetricType.SUMMARY_SKIPPED, atMillis)] ?: 0
        )
    }

    fun setLastNotificationAt(epochMillis: Long) {
        database.transaction { db ->
            val values = ContentValues().apply {
                put("key", LAST_NOTIFICATION_KEY)
                put("value", epochMillis)
            }
            db.insertWithOnConflict("metrics", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun lastNotificationAt(): Long? = database.read { db ->
        db.rawQuery(
            "SELECT value FROM metrics WHERE key = ?",
            arrayOf(LAST_NOTIFICATION_KEY)
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }

    private fun readLong(db: SQLiteDatabase, key: String): Long =
        db.rawQuery("SELECT value FROM metrics WHERE key = ?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        }

    private fun dailyKey(type: MetricType, atMillis: Long): String =
        localDate(atMillis) + ":" + type.suffix

    private fun localDate(atMillis: Long): String =
        Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    companion object {
        private const val LAST_NOTIFICATION_KEY = "last_notification_at"
    }
}
