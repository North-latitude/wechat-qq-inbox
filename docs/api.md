# API contract (v1)

Base path: `/api/v1`. All endpoints except `GET /health` require:

```
Authorization: Bearer <APP_TOKEN>
```

Timestamps are ISO-8601 UTC (`2026-10-22T12:00:00.000Z`). `source` is `wechat` or `qq`.
Errors use `{"detail": "..."}` with a 4xx/5xx status. Request bodies are JSON.

## Health and ping

### `GET /health` (public)

```json
{"status":"ok","version":"0.1.0","time":"2026-10-22T12:00:00Z","frontend":true}
```

### `GET /ping`

Returns `{"ok":true,"server_time":"..."}`. Used by the Android "test connection" button.

## Messages

### `POST /messages/batch`

Upload only rule-matched messages. At most 50 per request. Duplicate `client_id`s are ignored,
so retries are safe.

```json
{
  "device_id": "pixel-7",
  "messages": [
    {
      "client_id": "9f2c...64-hex",
      "source": "wechat",
      "conversation": "家人群",
      "sender": "妈妈",
      "content": "周六回家吃饭吗？",
      "message_time": "2026-10-22T11:59:30.000Z",
      "matched_rules": [
        {"id": "r1", "type": "group", "pattern": "家人群"},
        {"id": "r2", "type": "keyword", "pattern": "回家"}
      ]
    }
  ]
}
```

Response:

```json
{
  "accepted": 1,
  "duplicates": 0,
  "items": [{"client_id":"9f2c...","id":42,"duplicate":false}]
}
```

Validation rules: 1–50 messages; `source` enum; non-empty `conversation` (≤200) and `content`
(≤8000); `sender` ≤200; 1–20 `matched_rules` (each `id` ≤100, `type` enum, `pattern` ≤200);
`client_id` 8–128 chars.

### `GET /messages`

Query parameters:

| name | meaning |
|---|---|
| `source` | `wechat` / `qq` |
| `conversation` | exact match |
| `q` | substring search over content, conversation, sender |
| `unread` | `true` to return only unread |
| `limit` | 1–100, default 50 |
| `before_time` + `before_id` | cursor from a previous `next_cursor` |

Sorted by `message_time DESC, id DESC`.

```json
{
  "items": [
    {
      "id": 42,
      "client_id": "9f2c...",
      "source": "wechat",
      "conversation": "家人群",
      "sender": "妈妈",
      "content": "周六回家吃饭吗？",
      "message_time": "2026-10-22T11:59:30.000Z",
      "received_at": "2026-10-22T12:00:02.000Z",
      "matched_rules": [{"id":"r1","type":"group","pattern":"家人群"}],
      "is_read": false,
      "read_at": null
    }
  ],
  "has_more": false,
  "next_cursor": null
}
```

`next_cursor` is `{"before_time":"...","before_id":42}` when `has_more` is true.

### `GET /conversations`

Returns distinct conversations with unread counts, ordered by latest message.

```json
{"items":[{"source":"qq","conversation":"项目群","unread_count":3,"last_message_at":"..."}]}
```

### `GET /rules/hits?limit=20`

Returns recent messages plus their matched rules. This is a convenience view over the same
messages (not a separate audit log); the authoritative rule-hit record is `matched_rules` on each
message.

### `POST /messages/{id}/read`

Idempotent. Returns the message plus `today_unread` and `total_unread`.

### `POST /messages/read`

```json
{"ids": [1, 2, 3]}
```

Returns `{"updated": 2, "today_unread": 0, "total_unread": 0}`. Maximum 500 ids.

### `POST /messages/read-all`

Marks all unread messages matching optional `source`, `conversation`, `q` filters as read.
Returns `{"updated": 12, "today_unread": 0, "total_unread": 0}`.

## Collector

### `POST /collector/heartbeat`

Metadata only. Never include message text.

```json
{
  "device_id": "pixel-7",
  "app_version": "0.1.0",
  "listener_connected": true,
  "last_notification_at": "2026-10-22T11:59:30.000Z",
  "queue_size": 0,
  "battery_optimization_ignored": true,
  "counters": {
    "notifications_seen": 120,
    "matched": 8,
    "skipped_no_rule": 108,
    "duplicates": 4,
    "parse_errors": 0,
    "summary_skipped": 3
  },
  "rules": [
    {"id":"r1","type":"group","enabled":true},
    {"id":"r2","type":"keyword","enabled":false}
  ]
}
```

Counters are "today" counters maintained on the phone. `last_notification_at` may be `null`.
Rule entries intentionally omit `pattern`; group names, contact names, and keywords stay on the
phone. A pattern is uploaded only as part of a matched message's `matched_rules`.

### `GET /status`

```json
{
  "now": "2026-10-22T12:01:00Z",
  "collector": {
    "configured": true,
    "online": true,
    "verdict": "ok",
    "device_id": "pixel-7",
    "app_version": "0.1.0",
    "listener_connected": true,
    "last_seen": "2026-10-22T12:00:45Z",
    "seconds_since_seen": 15,
    "last_notification_at": "2026-10-22T11:59:30Z",
    "queue_size": 0,
    "battery_optimization_ignored": true,
    "counters": {"notifications_seen":120,"matched":8,"skipped_no_rule":108,"duplicates":4,"parse_errors":0,"summary_skipped":3},
    "rules": [],
    "rules_count": 2
  },
  "messages": {
    "last_message_at": "2026-10-22T11:59:30Z",
    "seconds_since_last_message": 90,
    "today_unread": 1,
    "total_unread": 3,
    "today_count": 8
  },
  "stale": {
    "threshold_minutes": 120,
    "no_recent_messages": false,
    "possible_collector_down": false,
    "reasons": []
  }
}
```

`collector.verdict` is one of `ok`, `never_seen`, `offline`, `listener_disconnected`.
`stale.possible_collector_down` is true when the collector is offline **or** no matched message
has arrived for `APP_STALE_MINUTES` while the collector is online.
`stale.reasons` contains machine-readable reasons: `collector_offline`,
`listener_disconnected`, `no_recent_messages`.

## WebSocket

1. `POST /ws-ticket` → `{"ticket":"...","expires_in":60}`. Tickets are single-use.
2. Connect to `/ws` and send as the **first frame** within 5 seconds:

```json
{"type":"auth","ticket":"..."}
```

3. On success: `{"type":"hello","data":{"server_time":"..."}}`, then events:

```json
{"type":"message","data":{...message...}}
{"type":"read","data":{"ids":[1],"count":1,"today_unread":0,"total_unread":2}}
{"type":"collector","data":{...collector status...}}
```

Close code `4401` means missing/invalid/expired ticket. Clients should fall back to polling and
request a new ticket before reconnecting. The token is never sent in the WebSocket URL.

## Static app

`GET /` serves the built PWA (`web/dist`) when present. API and WebSocket routes take precedence.
