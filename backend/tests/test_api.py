from __future__ import annotations

from datetime import datetime, timedelta, timezone

from conftest import AUTH, heartbeat_payload, message_payload


def test_health_is_public(client):
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["frontend"] is False


def test_protected_endpoint_requires_bearer(client):
    assert client.get("/api/v1/status").status_code == 401
    assert (
        client.get("/api/v1/status", headers={"Authorization": "Bearer wrong-token-000000"}).status_code
        == 401
    )
    assert client.get("/api/v1/status", headers=AUTH).status_code == 200


def test_batch_is_idempotent_and_rejects_unmatched(client):
    payload = {"device_id": "phone", "messages": [message_payload()]}
    first = client.post("/api/v1/messages/batch", headers=AUTH, json=payload)
    assert first.status_code == 200
    assert first.json()["accepted"] == 1
    message_id = first.json()["items"][0]["id"]

    second = client.post("/api/v1/messages/batch", headers=AUTH, json=payload)
    assert second.status_code == 200
    assert second.json()["accepted"] == 0
    assert second.json()["duplicates"] == 1
    assert second.json()["items"][0]["id"] == message_id

    listed = client.get("/api/v1/messages", headers=AUTH).json()
    assert len(listed["items"]) == 1
    assert listed["items"][0]["conversation"] == "家人群"

    unmatched = message_payload(client_id="b" * 64, matched_rules=[])
    response = client.post(
        "/api/v1/messages/batch",
        headers=AUTH,
        json={"device_id": "phone", "messages": [unmatched]},
    )
    assert response.status_code == 422


def test_filters_search_pagination_and_conversations(client):
    for index, (source, conversation, content) in enumerate(
        [
            ("wechat", "家人群", "晚上七点吃饭"),
            ("qq", "项目群", "接口联调完成"),
            ("wechat", "张三", "发票抬头发我"),
        ]
    ):
        payload = message_payload(
            client_id=f"{index + 1:064d}",
            source=source,
            conversation=conversation,
            content=content,
            message_time=f"2026-10-22T10:0{index}:00.000Z",
            matched_rules=[{"id": "r1", "type": "keyword", "pattern": "吃饭"}],
        )
        assert (
            client.post(
                "/api/v1/messages/batch",
                headers=AUTH,
                json={"device_id": "phone", "messages": [payload]},
            ).status_code
            == 200
        )

    wechat = client.get("/api/v1/messages?source=wechat", headers=AUTH).json()
    assert {item["conversation"] for item in wechat["items"]} == {"家人群", "张三"}

    searched = client.get("/api/v1/messages?q=联调", headers=AUTH).json()
    assert [item["conversation"] for item in searched["items"]] == ["项目群"]

    by_conversation = client.get(
        "/api/v1/messages?conversation=家人群", headers=AUTH
    ).json()
    assert len(by_conversation["items"]) == 1

    page_one = client.get("/api/v1/messages?limit=2", headers=AUTH).json()
    assert page_one["has_more"] is True
    assert len(page_one["items"]) == 2
    cursor = page_one["next_cursor"]
    page_two = client.get(
        f"/api/v1/messages?limit=2&before_time={cursor['before_time']}&before_id={cursor['before_id']}",
        headers=AUTH,
    ).json()
    assert page_two["has_more"] is False
    assert len(page_two["items"]) == 1

    conversations = client.get("/api/v1/conversations", headers=AUTH).json()["items"]
    assert len(conversations) == 3
    assert conversations[0]["last_message_at"] >= conversations[-1]["last_message_at"]


def test_read_single_bulk_and_read_all(client):
    ids = []
    now = datetime.now(timezone.utc)
    for index, source in enumerate(["wechat", "wechat", "qq"]):
        payload = message_payload(
            client_id=f"{index + 10:064d}",
            source=source,
            conversation="群A" if source == "wechat" else "群B",
            content=f"消息 {index}",
            message_time=(now - timedelta(minutes=index + 1)).isoformat().replace("+00:00", "Z"),
        )
        response = client.post(
            "/api/v1/messages/batch",
            headers=AUTH,
            json={"device_id": "phone", "messages": [payload]},
        )
        ids.append(response.json()["items"][0]["id"])

    status = client.get("/api/v1/status", headers=AUTH).json()
    assert status["messages"]["total_unread"] == 3
    assert status["messages"]["today_unread"] == 3

    single = client.post(f"/api/v1/messages/{ids[0]}/read", headers=AUTH)
    assert single.status_code == 200
    assert single.json()["message"]["is_read"] is True
    assert single.json()["total_unread"] == 2

    bulk = client.post("/api/v1/messages/read", headers=AUTH, json={"ids": ids[:2]})
    assert bulk.status_code == 200
    assert bulk.json()["total_unread"] == 1

    read_all = client.post(
        "/api/v1/messages/read-all", headers=AUTH, json={"source": "qq"}
    )
    assert read_all.status_code == 200
    assert read_all.json()["updated"] == 1
    assert read_all.json()["total_unread"] == 0

    missing = client.post("/api/v1/messages/99999/read", headers=AUTH)
    assert missing.status_code == 404


def test_heartbeat_and_status_verdicts(client):
    initial = client.get("/api/v1/status", headers=AUTH).json()
    assert initial["collector"]["configured"] is False
    assert initial["collector"]["verdict"] == "never_seen"
    assert initial["stale"]["possible_collector_down"] is True

    response = client.post(
        "/api/v1/collector/heartbeat", headers=AUTH, json=heartbeat_payload()
    )
    assert response.status_code == 200
    assert response.json()["collector"]["online"] is True

    status = client.get("/api/v1/status", headers=AUTH).json()
    assert status["collector"]["verdict"] == "ok"
    assert status["collector"]["counters"]["matched"] == 2
    assert status["collector"]["rules_count"] == 1
    assert status["stale"]["reasons"] == ["no_recent_messages"]

    disconnected = client.post(
        "/api/v1/collector/heartbeat",
        headers=AUTH,
        json=heartbeat_payload(listener_connected=False),
    )
    assert disconnected.status_code == 200
    status = client.get("/api/v1/status", headers=AUTH).json()
    assert status["collector"]["verdict"] == "listener_disconnected"
    assert "listener_disconnected" in status["stale"]["reasons"]

    with client.app.state.db.connect() as conn:
        conn.execute(
            "UPDATE collectors SET last_seen = '2020-01-01T00:00:00.000Z'"
        )
    offline = client.get("/api/v1/status", headers=AUTH).json()
    assert offline["collector"]["verdict"] == "offline"
    assert "collector_offline" in offline["stale"]["reasons"]


def test_websocket_ticket_and_realtime_message(client):
    ticket = client.post("/api/v1/ws-ticket", headers=AUTH).json()
    assert ticket["expires_in"] == 60

    with client.websocket_connect("/ws") as websocket:
        websocket.send_json({"type": "auth", "ticket": ticket["ticket"]})
        hello = websocket.receive_json()
        assert hello["type"] == "hello"

        response = client.post(
            "/api/v1/messages/batch",
            headers=AUTH,
            json={"device_id": "phone", "messages": [message_payload()]},
        )
        assert response.status_code == 200
        event = websocket.receive_json()
        assert event["type"] == "message"
        assert event["data"]["conversation"] == "家人群"

    reused = client.post("/api/v1/ws-ticket", headers=AUTH).json()
    with client.websocket_connect("/ws") as websocket:
        websocket.send_json({"type": "auth", "ticket": reused["ticket"]})
        websocket.receive_json()  # hello
        websocket.send_json({"type": "ping"})
        assert websocket.receive_json()["type"] == "pong"


def test_ws_rejects_reused_ticket(client):
    ticket = client.post("/api/v1/ws-ticket", headers=AUTH).json()["ticket"]
    with client.websocket_connect("/ws") as websocket:
        websocket.send_json({"type": "auth", "ticket": ticket})
        assert websocket.receive_json()["type"] == "hello"

    with client.websocket_connect("/ws") as websocket:
        websocket.send_json({"type": "auth", "ticket": ticket})
        try:
            message = websocket.receive_json()
        except Exception:
            message = None
        assert message is None
