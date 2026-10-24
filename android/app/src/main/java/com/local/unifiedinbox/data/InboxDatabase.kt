package com.local.unifiedinbox.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class InboxDatabase(context: Context) {
    private val databaseLock = Any()

    private val helper = object : SQLiteOpenHelper(
        context.applicationContext,
        DB_NAME,
        null,
        DB_VERSION
    ) {
        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE dedup(" +
                    "key TEXT PRIMARY KEY, " +
                    "created_at INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE outbox(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "client_id TEXT UNIQUE NOT NULL, " +
                    "payload TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "attempts INTEGER NOT NULL DEFAULT 0, " +
                    "state TEXT NOT NULL DEFAULT 'pending', " +
                    "last_error TEXT)"
            )
            db.execSQL(
                "CREATE TABLE hit_log(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "client_id TEXT, " +
                    "conversation TEXT, " +
                    "rules TEXT, " +
                    "created_at INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE metrics(" +
                    "key TEXT PRIMARY KEY, " +
                    "value INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE messages(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "client_id TEXT UNIQUE NOT NULL, " +
                    "source TEXT NOT NULL, " +
                    "conversation TEXT NOT NULL, " +
                    "sender TEXT, " +
                    "content TEXT NOT NULL, " +
                    "message_time INTEGER NOT NULL, " +
                    "matched_rules TEXT NOT NULL, " +
                    "is_read INTEGER NOT NULL DEFAULT 0, " +
                    "created_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX idx_outbox_state_id ON outbox(state, id)")
            db.execSQL("CREATE INDEX idx_hit_log_created_at ON hit_log(created_at)")
            db.execSQL("CREATE INDEX idx_dedup_created_at ON dedup(created_at)")
            db.execSQL("CREATE INDEX idx_messages_time ON messages(message_time DESC, id DESC)")
            db.execSQL("CREATE INDEX idx_messages_read ON messages(is_read, message_time DESC)")
            db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation, message_time DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Never drop the local inbox during an upgrade. v1 -> v2 only adds the
            // local messages table used by the phone-only inbox.
            if (oldVersion < 2) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS messages(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "client_id TEXT UNIQUE NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "conversation TEXT NOT NULL, " +
                        "sender TEXT, " +
                        "content TEXT NOT NULL, " +
                        "message_time INTEGER NOT NULL, " +
                        "matched_rules TEXT NOT NULL, " +
                        "is_read INTEGER NOT NULL DEFAULT 0, " +
                        "created_at INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_time ON messages(message_time DESC, id DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_read ON messages(is_read, message_time DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation, message_time DESC)")
            }
        }
    }

    fun <T> read(block: (SQLiteDatabase) -> T): T = synchronized(databaseLock) {
        block(helper.writableDatabase)
    }

    fun <T> transaction(block: (SQLiteDatabase) -> T): T = synchronized(databaseLock) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    fun close() {
        synchronized(databaseLock) {
            helper.close()
        }
    }

    companion object {
        private const val DB_NAME = "collector.db"
        private const val DB_VERSION = 2
    }
}
