from __future__ import annotations

import json
import sqlite3

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query, Request
from fastapi.responses import JSONResponse

from .auth import require_token
from .schemas import (
    HeartbeatRequest,
    IncomingMessage,
    MessageBatch,
    ReadAllRequest,
    ReadIdsRequest,
    Source,
)
from .services import (
    build_status,
    json_loads,
    serialize_message,
    unread_stats,
)
from .timeutil import now_iso, parse_iso, to_iso

router = APIRouter(prefix="/api/v1", dependencies=[Depends(require_token)])


def _db(request: Request):
    return request.app.state.db


def _settings(request: Request):
    return request.app.state.settings


def _escape_like(value: str) -> str:
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def _message_filters(
    source: Source | None,
    conversation: str | None,
    q: str | None,
    unread: bool | None = None,
) -> tuple[list[str], list]:
    clauses: list[str] = []
    params: list = []
    if source:
        clauses.append("source = ?")
        params.append(source)
    if conversation:
        clauses.append("conversation = ?")
        params.append(conversation.strip())
    if q and q.strip():
        pattern = f"%{_escape_like(q.strip())}%"
        clauses.append(
            "(content LIKE ? ESCAPE '\\' OR conversation LIKE ? ESCAPE '\\' "
            "OR IFNULL(sender, '') LIKE ? ESCAPE '\\')"
        )
        params.extend([pattern, pattern, pattern])
    if unread is True:
        clauses.append("is_read = 0")
    return clauses, params


async def _broadcast_message(request: Request, message: dict) -> None:
    await request.app.state.manager.broadcast({"type": "message", "data": message})


async def _broadcast_status(request: Request) -> None:
    db = _db(request)
    with db.connect() as conn:
        status = build_status(conn, _settings(request))
    await request.app.state.manager.broadcast(
        {"type": "collector", "data": status["collector"]}
    )


async def _broadcast_read(request: Request, ids: list[int]) -> None:
    db = _db(request)
    with db.connect() as conn:
        stats = unread_stats(conn, _settings(request))
    await request.app.state.manager.broadcast(
        {
            "type": "read",
            "data": {
                "ids": ids,
                "count": len(ids),
                "today_unread": stats["today_unread"],
                "total_unread": stats["total_unread"],
            },
        }
    )


@router.get("/ping")
def ping(request: Request):
    return {"ok": True, "server_time": now_iso()}


@router.post("/messages/batch")
def ingest_messages(
    batch: MessageBatch, request: Request, background: BackgroundTasks
):
    db = _db(request)
    settings = _settings(request)
    now = now_iso()
    accepted: list[dict] = []
    items: list[dict] = []

    conn = db.connect()
    try:
        conn.execute("BEGIN IMMEDIATE")
        for message in batch.messages:
            message_time = to_iso(message.message_time, settings.timezone)
            rules = [rule.model_dump() for rule in message.matched_rules]
            cursor = conn.execute(
                """
                INSERT OR IGNORE INTO messages
                    (client_id, source, conversation, sender, content, message_time,
                     received_at, matched_rules, device_id, is_read)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                (
                    message.client_id,
                    message.source,
                    message.conversation,
                    message.sender,
                    message.content,
                    message_time,
                    now,
                    json.dumps(rules, ensure_ascii=False, separators=(",", ":")),
                    batch.device_id,
                ),
            )
            if cursor.rowcount == 1:
                message_id = int(cursor.lastrowid)
                accepted.append(
                    {
                        "id": message_id,
                        "client_id": message.client_id,
                        "source": message.source,
                        "conversation": message.conversation,
                        "sender": message.sender,
                        "content": message.content,
                        "message_time": message_time,
                        "received_at": now,
                        "matched_rules": rules,
                        "is_read": False,
                        "read_at": None,
                    }
                )
                items.append(
                    {"client_id": message.client_id, "id": message_id, "duplicate": False}
                )
            else:
                row = conn.execute(
                    "SELECT id FROM messages WHERE client_id = ?", (message.client_id,)
                ).fetchone()
                items.append(
                    {
                        "client_id": message.client_id,
                        "id": int(row["id"]) if row else None,
                        "duplicate": True,
                    }
                )
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise
    finally:
        conn.close()

    for message in accepted:
        background.add_task(_broadcast_message, request, message)

    return {
        "accepted": len(accepted),
        "duplicates": len(items) - len(accepted),
        "items": items,
    }


@router.get("/messages")
def list_messages(
    request: Request,
    source: Source | None = None,
    conversation: str | None = Query(default=None, max_length=200),
    q: str | None = Query(default=None, max_length=100),
    unread: bool | None = None,
    limit: int = Query(default=50, ge=1, le=100),
    before_time: str | None = None,
    before_id: int | None = None,
):
    if (before_time is None) != (before_id is None):
        raise HTTPException(status_code=422, detail="before_time and before_id are required together")

    clauses, params = _message_filters(source, conversation, q, unread)
    if before_time is not None:
        try:
            canonical = to_iso(parse_iso(before_time, _settings(request).timezone))
        except (ValueError, TypeError) as exc:
            raise HTTPException(status_code=422, detail="invalid before_time") from exc
        clauses.append("(message_time < ? OR (message_time = ? AND id < ?))")
        params.extend([canonical, canonical, before_id])

    where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    sql = (
        "SELECT * FROM messages "
        f"{where} ORDER BY message_time DESC, id DESC LIMIT ?"
    )
    params.append(limit + 1)
    with _db(request).connect() as conn:
        rows = conn.execute(sql, params).fetchall()

    has_more = len(rows) > limit
    rows = rows[:limit]
    items = [serialize_message(row) for row in rows]
    next_cursor = None
    if has_more and items:
        last = items[-1]
        next_cursor = {"before_time": last["message_time"], "before_id": last["id"]}
    return {"items": items, "has_more": has_more, "next_cursor": next_cursor}


@router.get("/conversations")
def list_conversations(request: Request):
    with _db(request).connect() as conn:
        rows = conn.execute(
            """
            SELECT source, conversation,
                   SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END) AS unread_count,
                   MAX(message_time) AS last_message_at
            FROM messages
            GROUP BY source, conversation
            ORDER BY last_message_at DESC, conversation ASC
            """
        ).fetchall()
    return {
        "items": [
            {
                "source": row["source"],
                "conversation": row["conversation"],
                "unread_count": int(row["unread_count"] or 0),
                "last_message_at": row["last_message_at"],
            }
            for row in rows
        ]
    }


@router.post("/messages/{message_id}/read")
def read_message(message_id: int, request: Request, background: BackgroundTasks):
    db = _db(request)
    now = now_iso()
    conn = db.connect()
    changed = False
    try:
        conn.execute("BEGIN IMMEDIATE")
        row = conn.execute("SELECT * FROM messages WHERE id = ?", (message_id,)).fetchone()
        if row is None:
            conn.execute("ROLLBACK")
            raise HTTPException(status_code=404, detail="message not found")
        if not row["is_read"]:
            conn.execute(
                "UPDATE messages SET is_read = 1, read_at = ? WHERE id = ?",
                (now, message_id),
            )
            changed = True
        conn.execute("COMMIT")
        row = conn.execute("SELECT * FROM messages WHERE id = ?", (message_id,)).fetchone()
        stats = unread_stats(conn, _settings(request))
    except HTTPException:
        raise
    except Exception:
        try:
            conn.execute("ROLLBACK")
        except sqlite3.Error:
            pass
        raise
    finally:
        conn.close()

    if changed:
        background.add_task(_broadcast_read, request, [message_id])
    return {"message": serialize_message(row), **stats}


@router.post("/messages/read")
def read_messages(body: ReadIdsRequest, request: Request, background: BackgroundTasks):
    db = _db(request)
    now = now_iso()
    conn = db.connect()
    try:
        conn.execute("BEGIN IMMEDIATE")
        placeholders = ",".join("?" for _ in body.ids)
        rows = conn.execute(
            f"SELECT id FROM messages WHERE id IN ({placeholders}) AND is_read = 0",
            body.ids,
        ).fetchall()
        ids = [int(row["id"]) for row in rows]
        if ids:
            update_placeholders = ",".join("?" for _ in ids)
            conn.execute(
                f"UPDATE messages SET is_read = 1, read_at = ? "
                f"WHERE id IN ({update_placeholders})",
                [now, *ids],
            )
        conn.execute("COMMIT")
        stats = unread_stats(conn, _settings(request))
    except Exception:
        conn.execute("ROLLBACK")
        raise
    finally:
        conn.close()

    if ids:
        background.add_task(_broadcast_read, request, ids)
    return {"updated": len(ids), **stats}


@router.post("/messages/read-all")
def read_all(body: ReadAllRequest, request: Request, background: BackgroundTasks):
    clauses, params = _message_filters(
        body.source,
        body.conversation,
        body.q,
        unread=True,
    )
    where = f"WHERE {' AND '.join(clauses)}" if clauses else "WHERE is_read = 0"
    if clauses:
        where += " AND is_read = 0"

    db = _db(request)
    now = now_iso()
    conn = db.connect()
    try:
        conn.execute("BEGIN IMMEDIATE")
        rows = conn.execute(f"SELECT id FROM messages {where}", params).fetchall()
        ids = [int(row["id"]) for row in rows]
        if ids:
            placeholders = ",".join("?" for _ in ids)
            conn.execute(
                f"UPDATE messages SET is_read = 1, read_at = ? "
                f"WHERE id IN ({placeholders})",
                [now, *ids],
            )
        conn.execute("COMMIT")
        stats = unread_stats(conn, _settings(request))
    except Exception:
        conn.execute("ROLLBACK")
        raise
    finally:
        conn.close()

    if ids:
        background.add_task(_broadcast_read, request, ids)
    return {"updated": len(ids), **stats}


@router.post("/collector/heartbeat")
def heartbeat(body: HeartbeatRequest, request: Request, background: BackgroundTasks):
    settings = _settings(request)
    now = now_iso()
    last_notification = (
        to_iso(body.last_notification_at, settings.timezone)
        if body.last_notification_at is not None
        else None
    )
    counters = body.counters.model_dump()
    rules = [rule.model_dump() for rule in body.rules]
    db = _db(request)
    with db.connect() as conn:
        conn.execute("BEGIN IMMEDIATE")
        conn.execute(
            """
            INSERT INTO collectors
                (device_id, app_version, listener_connected, last_seen,
                 last_notification_at, queue_size, battery_optimization_ignored,
                 counters, rules, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                app_version = excluded.app_version,
                listener_connected = excluded.listener_connected,
                last_seen = excluded.last_seen,
                last_notification_at = excluded.last_notification_at,
                queue_size = excluded.queue_size,
                battery_optimization_ignored = excluded.battery_optimization_ignored,
                counters = excluded.counters,
                rules = excluded.rules,
                updated_at = excluded.updated_at
            """,
            (
                body.device_id,
                body.app_version,
                int(body.listener_connected),
                now,
                last_notification,
                body.queue_size,
                int(body.battery_optimization_ignored),
                json.dumps(counters, ensure_ascii=False, separators=(",", ":")),
                json.dumps(rules, ensure_ascii=False, separators=(",", ":")),
                now,
            ),
        )
        conn.execute("COMMIT")

    background.add_task(_broadcast_status, request)
    with db.connect() as conn:
        status = build_status(conn, settings)
    return {"ok": True, "server_time": now, "collector": status["collector"]}


@router.get("/status")
def status(request: Request):
    with _db(request).connect() as conn:
        return build_status(conn, _settings(request))


@router.post("/ws-ticket")
def ws_ticket(request: Request):
    ticket, expires_in = request.app.state.tickets.issue()
    return {"ticket": ticket, "expires_in": expires_in}


@router.get("/rules/hits")
def rule_hits(request: Request, limit: int = Query(default=20, ge=1, le=100)):
    """Recent messages with their matched rules, useful for diagnosing missed alerts."""
    with _db(request).connect() as conn:
        rows = conn.execute(
            "SELECT id, source, conversation, message_time, matched_rules "
            "FROM messages ORDER BY message_time DESC, id DESC LIMIT ?",
            (limit,),
        ).fetchall()
    return {
        "items": [
            {
                "id": row["id"],
                "source": row["source"],
                "conversation": row["conversation"],
                "message_time": row["message_time"],
                "matched_rules": json_loads(row["matched_rules"], []),
            }
            for row in rows
        ]
    }
