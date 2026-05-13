"""Rating endpoint: only completed rides are ratable, only participants
can rate, and a successful submit updates the target user's running
rating_avg + rating_count.
"""
from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest
from httpx import AsyncClient

from tests.conftest import auth_headers, dev_login


async def _completed_ride(client: AsyncClient, driver: dict, rider: dict) -> dict:
    body = {
        "origin": {"lat": 12.9716, "lng": 77.5946},
        "destination": {"lat": 12.9352, "lng": 77.6245},
        "origin_label": "MG Road",
        "destination_label": "Koramangala",
        "depart_at": (datetime.now(UTC) + timedelta(minutes=10)).isoformat(),
        "seats_total": 3,
        "price_per_seat": "75",
    }
    ride = (await client.post(
        "/v1/rides", json=body, headers=auth_headers(driver["token"])
    )).json()
    await client.post(
        f"/v1/rides/{ride['id']}/bookings",
        json={"seats": 1},
        headers=auth_headers(rider["token"]),
    )
    await client.post(
        f"/v1/rides/{ride['id']}/start", headers=auth_headers(driver["token"])
    )
    await client.post(
        f"/v1/rides/{ride['id']}/complete", headers=auth_headers(driver["token"])
    )
    return ride


@pytest.mark.asyncio
async def test_rating_updates_target_average(client: AsyncClient) -> None:
    driver = await dev_login(client, "+919000001001", "Driver")
    rider = await dev_login(client, "+919000001002", "Rider")
    ride = await _completed_ride(client, driver, rider)

    # Rider rates driver 5 stars.
    resp = await client.post(
        f"/v1/rides/{ride['id']}/ratings",
        json={"to_user_id": driver["user"]["id"], "stars": 5, "text": "smooth"},
        headers=auth_headers(rider["token"]),
    )
    assert resp.status_code == 201, resp.text

    # Driver's profile should now reflect the rating.
    me = await client.get("/v1/me", headers=auth_headers(driver["token"]))
    assert me.json()["rating_count"] == 1
    assert float(me.json()["rating_avg"]) == 5.0


@pytest.mark.asyncio
async def test_cannot_rate_uncompleted_ride(client: AsyncClient) -> None:
    driver = await dev_login(client, "+919000001010", "Driver")
    rider = await dev_login(client, "+919000001011", "Rider")

    body = {
        "origin": {"lat": 12.9716, "lng": 77.5946},
        "destination": {"lat": 12.9352, "lng": 77.6245},
        "origin_label": "MG Road",
        "destination_label": "Koramangala",
        "depart_at": (datetime.now(UTC) + timedelta(minutes=10)).isoformat(),
        "seats_total": 3,
        "price_per_seat": "75",
    }
    ride = (await client.post(
        "/v1/rides", json=body, headers=auth_headers(driver["token"])
    )).json()
    await client.post(
        f"/v1/rides/{ride['id']}/bookings",
        json={"seats": 1},
        headers=auth_headers(rider["token"]),
    )

    resp = await client.post(
        f"/v1/rides/{ride['id']}/ratings",
        json={"to_user_id": driver["user"]["id"], "stars": 5},
        headers=auth_headers(rider["token"]),
    )
    assert resp.status_code == 400
    assert "not completed" in resp.text.lower()


@pytest.mark.asyncio
async def test_non_participant_cannot_rate(client: AsyncClient) -> None:
    driver = await dev_login(client, "+919000001020", "Driver")
    rider = await dev_login(client, "+919000001021", "Rider")
    bystander = await dev_login(client, "+919000001022", "Bystander")
    ride = await _completed_ride(client, driver, rider)

    resp = await client.post(
        f"/v1/rides/{ride['id']}/ratings",
        json={"to_user_id": driver["user"]["id"], "stars": 5},
        headers=auth_headers(bystander["token"]),
    )
    assert resp.status_code == 403
