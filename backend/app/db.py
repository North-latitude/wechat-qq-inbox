from __future__ import annotations

import sqlite3
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id TEXT NOT NULL UNIQUE,
    source TEXT NOT NULL CHECK (source IN ('wechat', 'qq')),
    conversation TEXT NOT NULL,
    sender TEXT,
    content TEXT NOT NULL,
    message_time TEXT NOT NULL,
    received_at TEXT NOT NULL,
    matched_rules TEXT NOT NULL,
    device_id TEXT,
    is_read INTEGER NOT NULL DEFAULT 0,
    read_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_messages_time ON messages(message_time DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_messages_source_time ON messages(source, message_time DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_messages_unread ON messages(is_read, message_time DESC);
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(source, conversation, message_time DESC);

CREATE TABLE IF NOT EXISTS collectors (
    device_id TEXT PRIMARY KEY,
    app_version TEXT,
    listener_connected INTEGER NOT NULL DEFAULT 0,
    last_seen TEXT NOT NULL,
    last_notification_at TEXT,
    queue_size INTEGER NOT NULL DEFAULT 0,
    battery_optimization_ignored INTEGER NOT NULL DEFAULT 0,
    counters TEXT NOT NULL DEFAULT '{}',
    rules TEXT NOT NULL DEFAULT '[]',
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
"""


class Database:
    def __init__(self, path: Path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.path, timeout=10.0, isolation_level=None)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        conn.execute("PRAGMA busy_timeout = 5000")
        return conn

    def initialize(self) -> None:
        with self.connect() as conn:
            conn.execute("PRAGMA journal_mode = WAL")
            conn.executescript(SCHEMA)
            conn.execute(
                "INSERT INTO meta(key, value) VALUES('schema_version', '1') "
                "ON CONFLICT(key) DO NOTHING"
            )

    def transaction(self) -> sqlite3.Connection:
        conn = self.connect()
        conn.execute("BEGIN IMMEDIATE")
        return conn
