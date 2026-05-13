"""Shared pytest fixtures.

Provides a per-session schema bootstrap (alembic upgrade head) plus a
function-scoped fixture that truncates the working tables, so each test
starts on a clean slate without paying the cost of full re-migration.

Tests that touch the DB are skipped automatically when the DATABASE_URL
target is unreachable, so a developer without Postgres locally can still
run the pure-Python tests (`test_recurrence`, `test_geocode`).
"""
from __future__ import annotations

import os
from collections.abc import AsyncIterator

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from app.core.config import settings
from app.services import routing

# Auth router checks this at request time, so flipping it before any test
# importing the app is enough for /auth/dev-login to succeed.
os.environ.setdefault("DEV_LOGIN_ENABLED", "true")
settings.dev_login_enabled = True


# Tests shouldn't hit the live OSRM demo server on every ride.create —
# short-circuit to "no polyline this time" so the search endpoint falls
# back to its endpoint-radius behaviour. A dedicated routing test can
# monkeypatch this back to a real implementation if it needs to.
async def _noop_route(*_a, **_k):  # type: ignore[no-untyped-def]
    return None


routing.fetch_route = _noop_route  # type: ignore[assignment]


_TABLES = ["fcm_tokens", "ratings", "messages", "bookings", "rides", "users"]


async def _db_reachable() -> bool:
    engine = create_async_engine(settings.database_url, pool_pre_ping=True)
    try:
        async with engine.connect() as conn:
            await conn.execute(text("select 1"))
        return True
    except Exception:
        return False
    finally:
        await engine.dispose()


@pytest_asyncio.fixture(scope="session")
async def db_ready() -> bool:
    """Run alembic once per session against the configured DATABASE_URL.

    Returns True if the DB is reachable + migrated; False otherwise, so
    DB-dependent tests can pytest.skip cleanly instead of erroring.
    """
    if not await _db_reachable():
        return False

    # Run migrations via the alembic Python API so we don't shell out.
    from alembic import command
    from alembic.config import Config

    cfg = Config("alembic.ini")
    cfg.set_main_option("sqlalchemy.url", settings.database_url)
    command.upgrade(cfg, "head")
    return True


@pytest_asyncio.fixture
async def clean_db(db_ready: bool) -> AsyncIterator[None]:
    if not db_ready:
        pytest.skip("DATABASE_URL not reachable")

    engine = create_async_engine(settings.database_url)
    try:
        async with engine.begin() as conn:
            await conn.execute(text(f"TRUNCATE {', '.join(_TABLES)} CASCADE"))
        yield
    finally:
        await engine.dispose()


@pytest_asyncio.fixture
async def client(clean_db: None) -> AsyncIterator[AsyncClient]:
    """An httpx AsyncClient bound to the FastAPI app via ASGITransport."""
    from app.main import app

    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as ac:
        yield ac


async def dev_login(client: AsyncClient, phone: str, name: str | None = None) -> dict:
    """Helper: dev-login and return {token, user}. Authorisation header is
    NOT applied — caller decides which token to attach to subsequent calls."""
    resp = await client.post(
        "/v1/auth/dev-login", json={"phone": phone, "name": name}
    )
    assert resp.status_code == 200, resp.text
    return resp.json()


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}
