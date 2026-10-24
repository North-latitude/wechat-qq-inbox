from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator

Source = Literal["wechat", "qq"]
RuleType = Literal["group", "contact", "keyword"]

CLIENT_ID_PATTERN = r"^[A-Za-z0-9_-]{8,128}$"


class MatchedRule(BaseModel):
    id: str = Field(min_length=1, max_length=100)
    type: RuleType
    pattern: str = Field(min_length=1, max_length=200)

    @field_validator("id", "pattern")
    @classmethod
    def strip_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("must not be blank")
        return value


class IncomingMessage(BaseModel):
    client_id: str = Field(pattern=CLIENT_ID_PATTERN)
    source: Source
    conversation: str = Field(min_length=1, max_length=200)
    sender: str | None = Field(default=None, max_length=200)
    content: str = Field(min_length=1, max_length=8000)
    message_time: datetime
    matched_rules: list[MatchedRule] = Field(min_length=1, max_length=20)

    @field_validator("conversation", "content")
    @classmethod
    def strip_required(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("must not be blank")
        return value

    @field_validator("sender")
    @classmethod
    def normalize_sender(cls, value: str | None) -> str | None:
        if value is None:
            return None
        value = value.strip()
        return value or None


class MessageBatch(BaseModel):
    device_id: str = Field(min_length=1, max_length=100)
    messages: list[IncomingMessage] = Field(min_length=1, max_length=50)

    @field_validator("device_id")
    @classmethod
    def strip_device(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("must not be blank")
        return value


class Counters(BaseModel):
    notifications_seen: int = Field(default=0, ge=0, le=10_000_000)
    matched: int = Field(default=0, ge=0, le=10_000_000)
    skipped_no_rule: int = Field(default=0, ge=0, le=10_000_000)
    duplicates: int = Field(default=0, ge=0, le=10_000_000)
    parse_errors: int = Field(default=0, ge=0, le=10_000_000)
    summary_skipped: int = Field(default=0, ge=0, le=10_000_000)


class RuleInfo(BaseModel):
    """Rule metadata only. Patterns stay on the phone unless they matched a message."""

    id: str = Field(min_length=1, max_length=100)
    type: RuleType
    pattern: str | None = Field(default=None, max_length=200)
    enabled: bool = True

    @field_validator("id")
    @classmethod
    def strip_id(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("must not be blank")
        return value

    @field_validator("pattern")
    @classmethod
    def normalize_pattern(cls, value: str | None) -> str | None:
        if value is None:
            return None
        value = value.strip()
        return value or None


class HeartbeatRequest(BaseModel):
    device_id: str = Field(min_length=1, max_length=100)
    app_version: str = Field(default="", max_length=50)
    listener_connected: bool
    last_notification_at: datetime | None = None
    queue_size: int = Field(default=0, ge=0, le=100_000)
    battery_optimization_ignored: bool = False
    counters: Counters = Field(default_factory=Counters)
    rules: list[RuleInfo] = Field(default_factory=list, max_length=200)


class ReadIdsRequest(BaseModel):
    ids: list[int] = Field(min_length=1, max_length=500)

    @field_validator("ids")
    @classmethod
    def positive_ids(cls, value: list[int]) -> list[int]:
        cleaned = list(dict.fromkeys(value))
        if any(item <= 0 for item in cleaned):
            raise ValueError("ids must be positive")
        return cleaned


class ReadAllRequest(BaseModel):
    source: Source | None = None
    conversation: str | None = Field(default=None, max_length=200)
    q: str | None = Field(default=None, max_length=100)
