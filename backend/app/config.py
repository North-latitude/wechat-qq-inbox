from __future__ import annotations

import os
import secrets
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

from dotenv import load_dotenv

VERSION = "0.4.0"
ROOT_DIR = Path(__file__).resolve().parents[2]


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be an integer") from exc
    return value


def _read_or_create_token(data_dir: Path) -> str:
    token_file = data_dir / "token.txt"
    if token_file.exists():
        token = token_file.read_text(encoding="utf-8").strip()
        if len(token) >= 16:
            return token
    data_dir.mkdir(parents=True, exist_ok=True)
    token = secrets.token_urlsafe(32)
    token_file.write_text(token + "\n", encoding="utf-8")
    try:
        token_file.chmod(0o600)
    except OSError:
        pass
    return token


@dataclass(frozen=True)
class Settings:
    root_dir: Path = ROOT_DIR
    token: str = ""
    db_path: Path = ROOT_DIR / "backend" / "data" / "inbox.db"
    timezone: str = "Asia/Shanghai"
    collector_offline_seconds: int = 180
    stale_minutes: int = 120
    allowed_origins: tuple[str, ...] = field(default_factory=tuple)
    version: str = VERSION

    @property
    def data_dir(self) -> Path:
        return self.db_path.parent

    @property
    def web_dist(self) -> Path:
        return self.root_dir / "web" / "dist"

    def ensure_dirs(self) -> None:
        self.db_path.parent.mkdir(parents=True, exist_ok=True)


def load_settings() -> Settings:
    load_dotenv(ROOT_DIR / ".env", override=False)

    db_raw = os.getenv("APP_DB_PATH", "backend/data/inbox.db").strip()
    db_path = Path(db_raw)
    if not db_path.is_absolute():
        db_path = (ROOT_DIR / db_path).resolve()
    db_path.parent.mkdir(parents=True, exist_ok=True)

    token = os.getenv("APP_TOKEN", "").strip()
    if not token:
        token = _read_or_create_token(db_path.parent)
    if len(token) < 16:
        raise RuntimeError("APP_TOKEN must be at least 16 characters")
    if token.lower() in {"replace-with-a-long-random-token", "changeme", "change-me", "token"}:
        raise RuntimeError("APP_TOKEN is still a placeholder; use a random value")

    origins = tuple(
        origin.strip().rstrip("/")
        for origin in os.getenv("APP_ALLOWED_ORIGINS", "").split(",")
        if origin.strip()
    )

    return Settings(
        token=token,
        db_path=db_path,
        timezone=os.getenv("APP_TIMEZONE", "Asia/Shanghai").strip() or "Asia/Shanghai",
        collector_offline_seconds=max(30, _env_int("APP_COLLECTOR_OFFLINE_SECONDS", 180)),
        stale_minutes=max(5, _env_int("APP_STALE_MINUTES", 120)),
        allowed_origins=origins,
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    settings = load_settings()
    settings.ensure_dirs()
    return settings
