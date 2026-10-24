from __future__ import annotations

import asyncio
import json
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from .config import Settings, get_settings
from .db import Database
from .routes import router
from .timeutil import now_iso
from .ws import ConnectionManager, TicketStore, origin_allowed

logger = logging.getLogger("unified_inbox")

CSP = (
    "default-src 'self'; "
    "script-src 'self'; "
    "style-src 'self' 'unsafe-inline'; "
    "img-src 'self' data:; "
    "font-src 'self'; "
    "connect-src 'self' ws: wss:; "
    "manifest-src 'self'; "
    "worker-src 'self'; "
    "frame-ancestors 'none'; "
    "base-uri 'self'; "
    "form-action 'self'"
)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    if len(settings.token) < 16:
        raise RuntimeError("APP_TOKEN must be at least 16 characters")
    settings.ensure_dirs()

    database = Database(settings.db_path)
    manager = ConnectionManager()
    tickets = TicketStore()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        database.initialize()
        logger.info("database ready at %s", settings.db_path)
        if not os.getenv("APP_TOKEN", "").strip():
            logger.info("auth token loaded from %s", settings.db_path.parent / "token.txt")
        if not settings.web_dist.is_dir():
            logger.warning("web/dist not found; API only (run the web build)")
        yield

    app = FastAPI(
        title="Unified Inbox",
        version=settings.version,
        docs_url="/api/docs",
        redoc_url=None,
        openapi_url="/api/openapi.json",
        lifespan=lifespan,
    )
    app.state.settings = settings
    app.state.db = database
    app.state.manager = manager
    app.state.tickets = tickets

    @app.middleware("http")
    async def security_headers(request: Request, call_next):
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
        response.headers["Cross-Origin-Resource-Policy"] = "same-origin"
        response.headers["Server"] = "unified-inbox"
        if request.url.path.startswith("/api/"):
            response.headers["Cache-Control"] = "no-store"
        else:
            response.headers["Content-Security-Policy"] = CSP
        forwarded = request.headers.get("x-forwarded-proto", "")
        if request.url.scheme == "https" or forwarded == "https":
            response.headers["Strict-Transport-Security"] = "max-age=31536000"
        return response

    @app.get("/api/v1/health", tags=["public"])
    def health():
        return {
            "status": "ok",
            "version": settings.version,
            "time": now_iso(),
            "frontend": settings.web_dist.is_dir(),
        }

    @app.get("/health", include_in_schema=False)
    def health_short():
        return {"status": "ok", "time": now_iso()}

    app.include_router(router)

    @app.websocket("/ws")
    async def websocket_endpoint(websocket: WebSocket):
        if not origin_allowed(
            websocket.headers.get("origin"),
            websocket.headers.get("host"),
            settings.allowed_origins,
        ):
            await websocket.close(code=4403)
            return

        await websocket.accept()
        try:
            raw = await asyncio.wait_for(websocket.receive_text(), timeout=5.0)
        except (asyncio.TimeoutError, WebSocketDisconnect):
            await websocket.close(code=4401, reason="auth timeout")
            return

        try:
            payload = json.loads(raw)
        except ValueError:
            await websocket.close(code=4400, reason="invalid json")
            return
        if payload.get("type") != "auth" or not isinstance(payload.get("ticket"), str):
            await websocket.close(code=4401, reason="auth required")
            return
        if not tickets.consume(payload["ticket"]):
            await websocket.close(code=4401, reason="invalid ticket")
            return

        await manager.add(websocket)
        try:
            await websocket.send_json(
                {"type": "hello", "data": {"server_time": now_iso(), "version": settings.version}}
            )
            while True:
                raw = await websocket.receive_text()
                try:
                    incoming = json.loads(raw)
                except ValueError:
                    continue
                if incoming.get("type") == "ping":
                    await websocket.send_json(
                        {"type": "pong", "data": {"server_time": now_iso()}}
                    )
        except WebSocketDisconnect:
            pass
        except Exception:
            logger.debug("websocket closed unexpectedly", exc_info=True)
        finally:
            await manager.remove(websocket)

    @app.get("/", include_in_schema=False)
    def root(request: Request):
        index = settings.web_dist / "index.html"
        if index.exists():
            return FileResponse(index)
        return JSONResponse(
            {
                "message": "API is running, but the PWA is not built.",
                "build": "pnpm --dir web install && pnpm --dir web build",
                "health": "/api/v1/health",
            }
        )

    if settings.web_dist.is_dir():
        app.mount("/", StaticFiles(directory=settings.web_dist, html=True), name="web")

    return app


app = create_app()
