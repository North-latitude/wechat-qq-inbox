from __future__ import annotations

import json
import sqlite3
from typing import Any

from .config import Settings
from .timeutil import now_iso, seconds_since, start_of_day_iso, to_iso

DEFAULT_COUNTERS = {
    "notifications_seen": 0,
    "matched": 0,
    "skipped_no_rule": 0,
    "duplicates": 0,
    "parse_errors": 0,
    "summary_skipped": 0,
}


def json_loads(value: str | None, fallback: Any) -> Any:
    if not value:
        return fallback
    try:
        return json.loads(value)
    except (TypeError, ValueError):
        return fallback


def serialize_message(row: sqlite3.Row) -> dict:
    data = dict(row)
    data["matched_rules"] = json_loads(data.get("matched_rules"), [])
    data["is_read"] = bool(data.get("is_read"))
    return data


def unread_stats(conn: sqlite3.Connection, settings: Settings) -> dict:
    today_start = start_of_day_iso(settings.timezone)
    row = conn.execute(
        """
        SELECT
            COALESCE(SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END), 0) AS total_unread,
            COALESCE(SUM(CASE WHEN is_read = 0 AND message_time >= ? THEN 1 ELSE 0 END), 0) AS today_unread,
            COALESCE(SUM(CASE WHEN message_time >= ? THEN 1 ELSE 0 END), 0) AS today_count,
            MAX(message_time) AS last_message_at
        FROM messages
        """,
        (today_start, today_start),
    ).fetchone()
    return {
        "today_unread": int(row["today_unread"] or 0),
        "total_unread": int(row["total_unread"] or 0),
        "today_count": int(row["today_count"] or 0),
        "last_message_at": row["last_message_at"],
    }


def build_collector(conn: sqlite3.Connection, settings: Settings) -> dict:
    row = conn.execute(
        "SELECT * FROM collectors ORDER BY last_seen DESC LIMIT 1"
    ).fetchone()
    if row is None:
        return {
            "configured": False,
            "online": False,
            "verdict": "never_seen",
            "device_id": None,
            "app_version": None,
            "listener_connected": False,
            "last_seen": None,
            "seconds_since_seen": None,
            "last_notification_at": None,
            "queue_size": 0,
            "battery_optimization_ignored": False,
            "counters": dict(DEFAULT_COUNTERS),
            "rules": [],
            "rules_count": 0,
        }

    last_seen = row["last_seen"]
    age = seconds_since(last_seen)
    fresh = age is not None and age <= settings.collector_offline_seconds
    listener = bool(row["listener_connected"])
    if not fresh:
        verdict = "offline"
    elif not listener:
        verdict = "listener_disconnected"
    else:
        verdict = "ok"

    counters = dict(DEFAULT_COUNTERS)
    stored_counters = json_loads(row["counters"], {})
    if isinstance(stored_counters, dict):
        for key in counters:
            try:
                counters[key] = max(0, int(stored_counters.get(key, 0)))
            except (TypeError, ValueError):
                counters[key] = 0
    rules = json_loads(row["rules"], [])
    if not isinstance(rules, list):
        rules = []

    return {
        "configured": True,
        "online": verdict == "ok",
        "verdict": verdict,
        "device_id": row["device_id"],
        "app_version": row["app_version"],
        "listener_connected": listener,
        "last_seen": last_seen,
        "seconds_since_seen": age,
        "last_notification_at": row["last_notification_at"],
        "queue_size": int(row["queue_size"] or 0),
        "battery_optimization_ignored": bool(row["battery_optimization_ignored"]),
        "counters": counters,
        "rules": rules,
        "rules_count": len(rules),
    }


def build_status(conn: sqlite3.Connection, settings: Settings) -> dict:
    collector = build_collector(conn, settings)
    stats = unread_stats(conn, settings)
    last_message = stats["last_message_at"]
    message_age = seconds_since(last_message)
    no_recent = message_age is None or message_age > settings.stale_minutes * 60

    reasons: list[str] = []
    if not collector["configured"]:
        reasons.append("collector_offline")
    else:
        if collector["verdict"] == "offline":
            reasons.append("collector_offline")
        elif collector["verdict"] == "listener_disconnected":
            reasons.append("listener_disconnected")
        if no_recent:
            reasons.append("no_recent_messages")

    return {
        "now": now_iso(),
        "collector": collector,
        "messages": {
            "last_message_at": last_message,
            "seconds_since_last_message": message_age,
            **stats,
        },
        "stale": {
            "threshold_minutes": settings.stale_minutes,
            "no_recent_messages": no_recent,
            "possible_collector_down": bool(reasons),
            "reasons": list(dict.fromkeys(reasons)),
        },
    }


def normalize_time(value, settings: Settings) -> str:
    return to_iso(value, settings.timezone)
