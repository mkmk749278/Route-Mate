"""Per-ride WebSocket: chat + live driver location.

Authentication: `?token=<app jwt>`. Authorization: caller must be the driver
or have a non-cancelled booking on this ride.

Message envelope (JSON, both directions):
  {"type": "chat",     "body": "..."}
  {"type": "location", "lat": 12.97, "lng": 77.59, "ts": 1700000000}
Server fan-out adds `from`, `id`, `at` fields.
"""
from __future__ import annotations

import asyncio
import json
import logging
from datetime import UTC, datetime
from uuid import UUID, uuid4

import redis.asyncio as redis
from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect, status
from sqlalchemy import select

from app.core.config import settings
from app.core.security import decode_app_jwt
from app.db.models import Booking, BookingStatus, Message, Ride
from app.db.session import SessionLocal

log = logging.getLogger(__name__)
router = APIRouter()


def _channel(ride_id: UUID) -> str:
    return f"ride:{ride_id}"


def _location_key(ride_id: UUID) -> str:
    return f"ride:{ride_id}:loc"


async def _is_participant(ride_id: UUID, user_id: UUID) -> bool:
    async with SessionLocal() as s:
        ride = await s.get(Ride, ride_id)
        if ride is None:
            return False
        if ride.driver_id == user_id:
            return True
        booking = (
            await s.execute(
                select(Booking).where(
                    Booking.ride_id == ride_id,
                    Booking.rider_id == user_id,
                    Booking.status != BookingStatus.cancelled,
                )
            )
        ).scalar_one_or_none()
        return booking is not None


async def _is_driver(ride_id: UUID, user_id: UUID) -> bool:
    async with SessionLocal() as s:
        ride = await s.get(Ride, ride_id)
        return ride is not None and ride.driver_id == user_id


@router.websocket("/ride/{ride_id}")
async def ride_socket(
    ws: WebSocket,
    ride_id: UUID,
    token: str = Query(...),
) -> None:
    try:
        user_id = decode_app_jwt(token)
    except Exception:
        await ws.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    if not await _is_participant(ride_id, user_id):
        await ws.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await ws.accept()
    is_driver = await _is_driver(ride_id, user_id)

    r = redis.from_url(settings.redis_url, decode_responses=True)
    pubsub = r.pubsub()
    await pubsub.subscribe(_channel(ride_id))

    async def _pump_redis_to_ws() -> None:
        try:
            async for msg in pubsub.listen():
                if msg.get("type") != "message":
                    continue
                await ws.send_text(msg["data"])
        except Exception:
            log.exception("ws redis->ws pump error")

    pump = asyncio.create_task(_pump_redis_to_ws())

    try:
        while True:
            raw = await ws.receive_text()
            try:
                evt = json.loads(raw)
            except json.JSONDecodeError:
                continue

            etype = evt.get("type")
            if etype == "chat":
                body = (evt.get("body") or "").strip()
                if not body:
                    continue
                async with SessionLocal() as s:
                    m = Message(ride_id=ride_id, sender_id=user_id, body=body)
                    s.add(m)
                    await s.commit()
                    await s.refresh(m)
                payload = json.dumps(
                    {
                        "type": "chat",
                        "id": str(m.id),
                        "from": str(user_id),
                        "body": body,
                        "at": m.created_at.isoformat(),
                    }
                )
                await r.publish(_channel(ride_id), payload)

            elif etype == "location" and is_driver:
                lat = float(evt["lat"])
                lng = float(evt["lng"])
                ts = int(evt.get("ts") or datetime.now(UTC).timestamp())
                await r.set(
                    _location_key(ride_id),
                    json.dumps({"lat": lat, "lng": lng, "ts": ts}),
                    ex=settings.location_ttl_seconds,
                )
                payload = json.dumps(
                    {
                        "type": "location",
                        "id": str(uuid4()),
                        "from": str(user_id),
                        "lat": lat,
                        "lng": lng,
                        "ts": ts,
                    }
                )
                await r.publish(_channel(ride_id), payload)

    except WebSocketDisconnect:
        pass
    except Exception:
        log.exception("ws main loop error")
    finally:
        pump.cancel()
        try:
            await pubsub.unsubscribe(_channel(ride_id))
            await pubsub.aclose()
            await r.aclose()
        except Exception:
            pass
