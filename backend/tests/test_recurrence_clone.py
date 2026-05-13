"""End-to-end check that completing a recurring ride materialises exactly
one clone for the next matching weekday and is idempotent on re-completion.
"""
from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest
from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import create_async_engine

from app.core.config import settings
from app.db.models import Ride
from tests.conftest import auth_headers, dev_login


@pytest.mark.asyncio
async def test_complete_recurring_ride_materialises_one_clone(
    client: AsyncClient,
) -> None:
    driver = await dev_login(client, "+919000002001", "Recurring Driver")

    # Mon–Fri mask (bits 0..4) = 0b0011111 = 31.
    body = {
        "origin": {"lat": 12.9716, "lng": 77.5946},
        "destination": {"lat": 12.9352, "lng": 77.6245},
        "origin_label": "MG Road",
        "destination_label": "Koramangala",
        "depart_at": (datetime.now(UTC) + timedelta(minutes=5)).isoformat(),
        "seats_total": 2,
        "price_per_seat": "60",
        "recurrence_days": 0b0011111,
    }
    ride = (await client.post(
        "/v1/rides", json=body, headers=auth_headers(driver["token"])
    )).json()

    await client.post(
        f"/v1/rides/{ride['id']}/start", headers=auth_headers(driver["token"])
    )
    await client.post(
        f"/v1/rides/{ride['id']}/complete", headers=auth_headers(driver["token"])
    )

    engine = create_async_engine(settings.database_url)
    try:
        async with engine.connect() as conn:
            rows = (
                await conn.execute(
                    select(Ride.id, Ride.depart_at, Ride.status).where(
                        Ride.origin_label == "MG Road"
                    )
                )
            ).all()
    finally:
        await engine.dispose()

    # One original (now completed) + exactly one scheduled clone.
    statuses = sorted(str(r.status) for r in rows)
    assert statuses == ["completed", "scheduled"], f"got {statuses}"
    assert len(rows) == 2


@pytest.mark.asyncio
async def test_double_complete_is_idempotent(client: AsyncClient) -> None:
    """Completing twice (e.g. on a retry) must not create two clones.

    The endpoint guards on status, so the second `complete` will 409; the
    clone-materialiser itself is also guarded with an existence check on
    `(driver_id, depart_at, origin_label, destination_label)`.
    """
    driver = await dev_login(client, "+919000002010", "Driver")

    body = {
        "origin": {"lat": 12.9716, "lng": 77.5946},
        "destination": {"lat": 12.9352, "lng": 77.6245},
        "origin_label": "MG Road",
        "destination_label": "Koramangala",
        "depart_at": (datetime.now(UTC) + timedelta(minutes=5)).isoformat(),
        "seats_total": 2,
        "price_per_seat": "60",
        "recurrence_days": 0b1111111,  # every day
    }
    ride = (await client.post(
        "/v1/rides", json=body, headers=auth_headers(driver["token"])
    )).json()
    await client.post(
        f"/v1/rides/{ride['id']}/start", headers=auth_headers(driver["token"])
    )
    first = await client.post(
        f"/v1/rides/{ride['id']}/complete", headers=auth_headers(driver["token"])
    )
    assert first.status_code == 200
    second = await client.post(
        f"/v1/rides/{ride['id']}/complete", headers=auth_headers(driver["token"])
    )
    assert second.status_code == 409

    engine = create_async_engine(settings.database_url)
    try:
        async with engine.connect() as conn:
            count = (
                await conn.execute(
                    select(Ride.id).where(Ride.origin_label == "MG Road")
                )
            ).all()
    finally:
        await engine.dispose()

    assert len(count) == 2, f"expected 2 rides (original + 1 clone), got {len(count)}"
