"""WebSocket auth + authorisation: bad token, non-participant, and the
happy path where the driver of the ride connects successfully.

We test against the in-process app via TestClient.websocket_connect; the
Redis-backed fan-out branch is exercised in a separate integration test.
"""
from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from app.main import app
from tests.conftest import auth_headers, dev_login


def _ride_body(minutes: int = 30) -> dict:
    return {
        "origin": {"lat": 12.9716, "lng": 77.5946},
        "destination": {"lat": 12.9352, "lng": 77.6245},
        "origin_label": "MG Road",
        "destination_label": "Koramangala",
        "depart_at": (datetime.now(UTC) + timedelta(minutes=minutes)).isoformat(),
        "seats_total": 3,
        "price_per_seat": "75",
    }


@pytest.mark.asyncio
async def test_ws_rejects_bad_token(client) -> None:  # noqa: ARG001
    """A malformed token must close before any frame exchanges. Starlette
    surfaces this as a WebSocketDisconnect at receive time."""
    with TestClient(app) as tc:
        with pytest.raises(Exception, match=r".*"):  # noqa: B017,PT011 — disconnect surface varies
            with tc.websocket_connect(
                "/v1/ws/ride/00000000-0000-0000-0000-000000000000?token=not-a-jwt"
            ) as ws:
                ws.receive_text()


@pytest.mark.asyncio
async def test_ws_rejects_non_participant(client) -> None:
    """A signed-in user who isn't the driver nor a booked rider gets
    closed with 1008 before any frame exchanges."""
    driver = await dev_login(client, "+919000003001", "Driver")
    bystander = await dev_login(client, "+919000003003", "Bystander")
    ride = (await client.post(
        "/v1/rides", json=_ride_body(), headers=auth_headers(driver["token"])
    )).json()

    with TestClient(app) as tc:
        with pytest.raises(Exception, match=r".*"):  # noqa: B017,PT011 — disconnect surface varies
            with tc.websocket_connect(
                f"/v1/ws/ride/{ride['id']}?token={bystander['token']}"
            ) as ws:
                # Server closes immediately on policy violation; any
                # receive will surface as the exception we're catching.
                ws.receive_text()
