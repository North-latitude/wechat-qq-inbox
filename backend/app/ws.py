from __future__ import annotations

import asyncio
import json
import secrets
import threading
import time
from urllib.parse import urlsplit

from fastapi import WebSocket


class TicketStore:
    """Short-lived, single-use WebSocket tickets (never put the API token in a URL)."""

    def __init__(self, ttl_seconds: int = 60, max_tickets: int = 256):
        self.ttl_seconds = ttl_seconds
        self.max_tickets = max_tickets
        self._tickets: dict[str, float] = {}
        self._lock = threading.Lock()

    def issue(self) -> tuple[str, int]:
        ticket = secrets.token_urlsafe(32)
        now = time.monotonic()
        with self._lock:
            self._prune(now)
            while len(self._tickets) >= self.max_tickets:
                self._tickets.pop(next(iter(self._tickets)))
            self._tickets[ticket] = now + self.ttl_seconds
        return ticket, self.ttl_seconds

    def consume(self, ticket: str) -> bool:
        now = time.monotonic()
        with self._lock:
            expiry = self._tickets.pop(ticket, None)
        return expiry is not None and expiry >= now

    def _prune(self, now: float) -> None:
        for key in [key for key, expiry in self._tickets.items() if expiry < now]:
            self._tickets.pop(key, None)


class ConnectionManager:
    def __init__(self) -> None:
        self._connections: set[WebSocket] = set()
        self._lock = asyncio.Lock()

    async def add(self, websocket: WebSocket) -> None:
        async with self._lock:
            self._connections.add(websocket)

    async def remove(self, websocket: WebSocket) -> None:
        async with self._lock:
            self._connections.discard(websocket)

    async def broadcast(self, payload: dict) -> None:
        message = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        async with self._lock:
            targets = list(self._connections)
        dead: list[WebSocket] = []
        for websocket in targets:
            try:
                await websocket.send_text(message)
            except Exception:
                dead.append(websocket)
        if dead:
            async with self._lock:
                for websocket in dead:
                    self._connections.discard(websocket)

    @property
    def count(self) -> int:
        return len(self._connections)


def _host_key(netloc: str) -> str:
    value = netloc.strip().lower()
    for suffix in (":443", ":80"):
        if value.endswith(suffix):
            value = value[: -len(suffix)]
    return value


def origin_allowed(origin: str | None, host: str | None, allowed: tuple[str, ...]) -> bool:
    """Allow same-host browsers plus explicitly configured origins.

    Non-browser clients (Android, tests, curl) usually send no Origin header.
    """
    if not origin:
        return True
    normalized = origin.rstrip("/")
    if allowed:
        return normalized in {item.rstrip("/") for item in allowed}
    if not host:
        return False
    origin_host = _host_key(urlsplit(normalized).netloc)
    return origin_host == _host_key(host)
