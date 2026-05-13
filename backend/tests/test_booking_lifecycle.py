"""End-to-end booking lifecycle: post a ride, book it as a rider, then
exercise the accept/reject/cancel transitions plus the uniqueness and
self-booking guards.
"""
from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest
from httpx import AsyncClient

from tests.conftest import auth_headers, dev_login


def _depart_in(minutes: int) -> str:
    return (datetime.now(UTC) + timedelta(minutes=minutes)).isoformat()


async def _create_ride(client: AsyncClient, driver_token: str, **overrides) -> dict:
    body = {
        "origin": {"lat": 12.9716, "lng": 77.5946},
        "destination": {"lat": 12.9352, "lng": 77.6245},
        "origin_label": "MG Road",
        "destination_label": "Koramangala",
        "depart_at": _depart_in(30),
        "seats_total": 3,
        "price_per_seat": "75",
    }
    body.update(overrides)
    resp = await client.post("/v1/rides", json=body, headers=auth_headers(driver_token))
    assert resp.status_code == 201, resp.text
    return resp.json()


@pytest.mark.asyncio
async def test_full_booking_lifecycle(client: AsyncClient) -> None:
    driver = await dev_login(client, "+919000000001", "Driver D")
    rider = await dev_login(client, "+919000000002", "Rider R")

    ride = await _create_ride(client, driver["token"])

    book_resp = await client.post(
        f"/v1/rides/{ride['id']}/bookings",
        json={"seats": 1},
        headers=auth_headers(rider["token"]),
    )
    assert book_resp.status_code == 201, book_resp.text
    booking = book_resp.json()
    # Instant-book in v0.4+: the API short-circuits to accepted on create.
    assert booking["status"] == "accepted"

    # Idempotency: re-booking the same ride returns the existing booking,
    # not a duplicate row.
    again = await client.post(
        f"/v1/rides/{ride['id']}/bookings",
        json={"seats": 1},
        headers=auth_headers(rider["token"]),
    )
    assert again.status_code == 201
    assert again.json()["id"] == booking["id"]

    # Rider cancels their booking.
    cancel = await client.post(
        f"/v1/bookings/{booking['id']}/cancel",
        headers=auth_headers(rider["token"]),
    )
    assert cancel.status_code == 200
    assert cancel.json()["status"] == "cancelled"


@pytest.mark.asyncio
async def test_driver_cannot_book_own_ride(client: AsyncClient) -> None:
    driver = await dev_login(client, "+919000000010", "Solo Driver")
    ride = await _create_ride(client, driver["token"])

    resp = await client.post(
        f"/v1/rides/{ride['id']}/bookings",
        json={"seats": 1},
        headers=auth_headers(driver["token"]),
    )
    assert resp.status_code == 400
    assert "driver cannot book" in resp.text.lower()


@pytest.mark.asyncio
async def test_seats_available_decrements_on_booking(client: AsyncClient) -> None:
    driver = await dev_login(client, "+919000000020", "Driver")
    rider = await dev_login(client, "+919000000021", "Rider")

    ride = await _create_ride(client, driver["token"], seats_total=2)
    assert ride["seats_available"] == 2

    book = await client.post(
        f"/v1/rides/{ride['id']}/bookings",
        json={"seats": 1},
        headers=auth_headers(rider["token"]),
    )
    assert book.status_code == 201

    # Re-fetch the ride: one seat consumed.
    refreshed = await client.get(
        f"/v1/rides/{ride['id']}", headers=auth_headers(rider["token"])
    )
    assert refreshed.json()["seats_available"] == 1


@pytest.mark.asyncio
async def test_cancel_ride_cascades_to_bookings(client: AsyncClient) -> None:
    driver = await dev_login(client, "+919000000030", "Driver")
    rider = await dev_login(client, "+919000000031", "Rider")

    ride = await _create_ride(client, driver["token"])
    book = await client.post(
        f"/v1/rides/{ride['id']}/bookings",
        json={"seats": 1},
        headers=auth_headers(rider["token"]),
    )
    booking_id = book.json()["id"]

    cancel = await client.post(
        f"/v1/rides/{ride['id']}/cancel",
        headers=auth_headers(driver["token"]),
    )
    assert cancel.status_code == 200
    assert cancel.json()["status"] == "cancelled"

    # The booking must be cancelled too; the rider's view should reflect it.
    mine = await client.get(
        f"/v1/rides/{ride['id']}/booking/me",
        headers=auth_headers(rider["token"]),
    )
    assert mine.status_code == 200
    assert mine.json()["id"] == booking_id
    assert mine.json()["status"] == "cancelled"
