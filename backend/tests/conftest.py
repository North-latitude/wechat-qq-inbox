from __future__ import annotations

import os
import shutil
import sys
import uuid
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

os.environ.setdefault("APP_TOKEN", "test-token-0123456789abcdef")

from app.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402

TEST_TOKEN = "test-token-0123456789abcdef"
AUTH = {"Authorization": f"Bearer {TEST_TOKEN}"}


@pytest.fixture()
def tmp_path():
    """Create temp dirs with normal permissions.

    pytest's default tmpdir uses mode 0700, which the Windows sandbox turns into
    an ACL the confined process cannot reopen. Plain mkdir works.
    """
    base = BACKEND_DIR / ".test-tmp"
    base.mkdir(parents=True, exist_ok=True)
    path = base / uuid.uuid4().hex
    path.mkdir()
    try:
        yield path
    finally:
        shutil.rmtree(path, ignore_errors=True)


@pytest.fixture()
def client(tmp_path):
    settings = Settings(
        root_dir=tmp_path,
        token=TEST_TOKEN,
        db_path=tmp_path / "inbox.db",
        timezone="Asia/Shanghai",
        collector_offline_seconds=180,
        stale_minutes=120,
    )
    application = create_app(settings)
    with TestClient(application) as test_client:
        test_client.app = application
        yield test_client


def message_payload(**overrides):
    payload = {
        "client_id": "a" * 64,
        "source": "wechat",
        "conversation": "家人群",
        "sender": "妈妈",
        "content": "周六回家吃饭吗？",
        "message_time": "2026-10-22T11:59:30.000Z",
        "matched_rules": [{"id": "r1", "type": "group", "pattern": "家人群"}],
    }
    payload.update(overrides)
    return payload


def heartbeat_payload(**overrides):
    payload = {
        "device_id": "test-phone",
        "app_version": "0.1.0",
        "listener_connected": True,
        "last_notification_at": "2026-10-22T11:59:30.000Z",
        "queue_size": 0,
        "battery_optimization_ignored": True,
        "counters": {
            "notifications_seen": 10,
            "matched": 2,
            "skipped_no_rule": 8,
            "duplicates": 0,
            "parse_errors": 0,
            "summary_skipped": 0,
        },
        "rules": [{"id": "r1", "type": "group", "enabled": True}],
    }
    payload.update(overrides)
    return payload
