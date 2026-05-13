"""Structured JSON logging + per-request correlation IDs.

`configure_logging()` swaps the root handler for one that emits one JSON
line per record (`{ts, level, logger, msg, request_id, user_id, …}`).
`RequestContextMiddleware` populates `request_id` (from the inbound
`X-Request-ID` header if present, else a fresh UUID) into a contextvar,
so any logger inside the request handler picks it up automatically.
"""
from __future__ import annotations

import json
import logging
import sys
import time
from contextvars import ContextVar
from typing import Any
from uuid import uuid4

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp

request_id_var: ContextVar[str | None] = ContextVar("request_id", default=None)
user_id_var: ContextVar[str | None] = ContextVar("user_id", default=None)


class _JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "ts": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
        }
        rid = request_id_var.get()
        if rid is not None:
            payload["request_id"] = rid
        uid = user_id_var.get()
        if uid is not None:
            payload["user_id"] = uid
        if record.exc_info:
            payload["exc"] = self.formatException(record.exc_info)
        # Any structured fields a caller passed via `extra={...}`.
        for k, v in record.__dict__.items():
            if k in _RESERVED or k.startswith("_"):
                continue
            payload[k] = v
        return json.dumps(payload, default=str)


_RESERVED = {
    "name", "msg", "args", "levelname", "levelno", "pathname", "filename",
    "module", "exc_info", "exc_text", "stack_info", "lineno", "funcName",
    "created", "msecs", "relativeCreated", "thread", "threadName",
    "processName", "process", "message", "taskName",
}


def configure_logging(level: int = logging.INFO) -> None:
    """Reroute the root logger to a single JSON-emitting stderr handler.

    Idempotent: re-running it replaces the existing handlers in place.
    """
    handler = logging.StreamHandler(sys.stderr)
    handler.setFormatter(_JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level)
    # uvicorn ships its own access logger that double-logs request lines;
    # silence it since our middleware emits a richer record per request.
    logging.getLogger("uvicorn.access").handlers = []
    logging.getLogger("uvicorn.access").propagate = False


class RequestContextMiddleware(BaseHTTPMiddleware):
    """Stamp every request with a request_id and emit one access log line.

    Honours an inbound `X-Request-ID` header so callers (Caddy, a load
    balancer, a client) can correlate across hops. Always echoes the id
    back on the response so a debugging human can quote it.
    """

    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)
        self._log = logging.getLogger("routemate.access")

    async def dispatch(self, request: Request, call_next):  # type: ignore[override]
        rid = request.headers.get("x-request-id") or uuid4().hex[:16]
        token = request_id_var.set(rid)
        start = time.perf_counter()
        status = 500
        try:
            response: Response = await call_next(request)
            status = response.status_code
            response.headers["X-Request-ID"] = rid
            return response
        finally:
            self._log.info(
                "request",
                extra={
                    "method": request.method,
                    "path": request.url.path,
                    "status": status,
                    "latency_ms": round((time.perf_counter() - start) * 1000, 2),
                    "client": request.client.host if request.client else None,
                },
            )
            request_id_var.reset(token)
