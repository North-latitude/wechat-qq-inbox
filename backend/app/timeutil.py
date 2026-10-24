from __future__ import annotations

from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError


def zone(timezone_name: str):
    try:
        return ZoneInfo(timezone_name)
    except (ZoneInfoNotFoundError, ValueError):
        return timezone.utc


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def to_iso(value: datetime, timezone_name: str = "UTC") -> str:
    """Canonical UTC timestamp with millisecond precision and a trailing Z."""
    if value.tzinfo is None:
        value = value.replace(tzinfo=zone(timezone_name))
    value = value.astimezone(timezone.utc)
    return value.strftime("%Y-%m-%dT%H:%M:%S.") + f"{value.microsecond // 1000:03d}Z"


def now_iso() -> str:
    return to_iso(utc_now())


def parse_iso(value: str, timezone_name: str = "UTC") -> datetime:
    text = value.strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    parsed = datetime.fromisoformat(text)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=zone(timezone_name))
    return parsed.astimezone(timezone.utc)


def iso_after_seconds(seconds: int) -> str:
    return to_iso(utc_now() + timedelta(seconds=seconds))


def start_of_day_iso(timezone_name: str) -> str:
    local_now = utc_now().astimezone(zone(timezone_name))
    start = local_now.replace(hour=0, minute=0, second=0, microsecond=0)
    return to_iso(start)


def seconds_since(iso_value: str | None) -> int | None:
    if not iso_value:
        return None
    try:
        delta = utc_now() - parse_iso(iso_value)
    except (ValueError, TypeError):
        return None
    return max(0, int(delta.total_seconds()))
