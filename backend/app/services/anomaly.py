"""Phase 8: RideCheck-style anomaly detection.

Periodically scans every `started` ride that has a planned polyline,
compares the most recent driver location (from Redis, populated by the
WebSocket location frames) against the planned route, and fires a
single "everything OK?" push to the driver + every booked rider when
the off-route condition holds across N consecutive checks.

Designed for an in-process asyncio loop driven by `main.lifespan`. Each
ride's transient counter lives in Redis with a short TTL so it
naturally garbage-collects when the ride completes or the api restarts.
A `fired` flag (long TTL) prevents duplicate alerts for the same
deviation; clearing it when the driver returns to the corridor lets us
fire again on a subsequent deviation.

Stopped-vehicle detection and audio recording are tracked in
ROADMAP.md Phase 8 follow-ups.
"""
from __future__ import annotations

import asyncio
import json
import logging
from uuid import UUID

import redis.asyncio as redis
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.db.models import Booking, BookingStatus, Ride, RideStatus
from app.db.session import SessionLocal
from app.services import notifications as notify

log = logging.getLogger(__name__)

_OFF_ROUTE_COUNT_KEY = "ride:{rid}:off_route_count"
_OFF_ROUTE_FIRED_KEY = "ride:{rid}:off_route_fired"


async def _booked_rider_ids(session: AsyncSession, ride_id: UUID) -> list[UUID]:
    rows = await session.execute(
        select(Booking.rider_id).where(
            Booking.ride_id == ride_id,
            Booking.status == BookingStatus.accepted,
        )
    )
    return list(rows.scalars().all())


async def _evaluate_one(
    session: AsyncSession,
    r: redis.Redis,
    ride_id: UUID,
    driver_id: UUID,
) -> None:
    raw = await r.get(f"ride:{ride_id}:loc")
    if not raw:
        return
    try:
        loc = json.loads(raw)
        lat = float(loc["lat"])
        lng = float(loc["lng"])
    except (ValueError, KeyError, TypeError):
        return

    pt = func.ST_GeogFromText(f"SRID=4326;POINT({lng} {lat})")
    distance_m = (
        await session.execute(
            select(func.ST_Distance(Ride.polyline_geom, pt)).where(Ride.id == ride_id)
        )
    ).scalar_one()

    count_key = _OFF_ROUTE_COUNT_KEY.format(rid=ride_id)
    fired_key = _OFF_ROUTE_FIRED_KEY.format(rid=ride_id)

    if distance_m is None or distance_m <= settings.anomaly_off_route_meters:
        # Healthy: clear the running counter and the fired flag so the
        # next deviation gets to fire fresh.
        await r.delete(count_key, fired_key)
        return

    count = await r.incr(count_key)
    await r.expire(count_key, settings.anomaly_check_interval_seconds * 10)

    if count < settings.anomaly_off_route_consecutive_checks:
        return

    # One-shot per deviation. SET NX with a long TTL acts as the lock.
    fired = not await r.set(
        fired_key, "1", nx=True, ex=60 * 60
    )
    if fired:
        return

    log.info(
        "anomaly: ride=%s off route by %.0fm for %d consecutive checks",
        ride_id, float(distance_m), int(count),
    )

    body = (
        "We noticed your ride is off the planned route — let us know if "
        "you need help."
    )
    await notify.notify_user(
        session, driver_id,
        kind="safety.off_route",
        title="Everything OK?",
        body=body,
        data={"ride_id": str(ride_id)},
    )
    for rider_id in await _booked_rider_ids(session, ride_id):
        await notify.notify_user(
            session, rider_id,
            kind="safety.off_route",
            title="Ride off route",
            body=body,
            data={"ride_id": str(ride_id)},
        )


async def evaluate_started_rides(session: AsyncSession) -> int:
    """One pass over every started ride that has a planned polyline.

    Returns the number of rides actually evaluated (i.e. with a recent
    driver location fix). Useful for tests.
    """
    rows = (
        await session.execute(
            select(Ride.id, Ride.driver_id).where(
                Ride.status == RideStatus.started,
                Ride.polyline_geom.isnot(None),
            )
        )
    ).all()
    if not rows:
        return 0

    r = redis.from_url(settings.redis_url, decode_responses=True)
    evaluated = 0
    try:
        for ride_id, driver_id in rows:
            try:
                await _evaluate_one(session, r, ride_id, driver_id)
                evaluated += 1
            except Exception:  # noqa: BLE001
                log.exception("anomaly check failed for ride %s", ride_id)
    finally:
        await r.aclose()
    return evaluated


async def run_loop() -> None:
    """Tail call for `main.lifespan` — pumps `evaluate_started_rides`
    every `anomaly_check_interval_seconds`. Errors are logged and the
    loop keeps running.
    """
    interval = settings.anomaly_check_interval_seconds
    while True:
        try:
            async with SessionLocal() as session:
                await evaluate_started_rides(session)
        except Exception:  # noqa: BLE001
            log.exception("anomaly loop iteration failed")
        await asyncio.sleep(interval)
