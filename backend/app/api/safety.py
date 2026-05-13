"""Phase 2 (Safety v1) endpoints:

  POST   /v1/rides/{id}/share           — generate a share token
  POST   /v1/rides/{id}/incidents       — record an incident; notify the
                                           reporter's trusted contacts
  POST   /v1/users/{id}/block           — add to the caller's block list
  DELETE /v1/users/{id}/block           — remove from the caller's block list
  GET    /v1/share/{token}              — public read-only view of a ride

The public share endpoint is the only un-authenticated route in this
file; everything else uses the standard bearer-token dependency.
"""
from __future__ import annotations

import json
import logging
import secrets
from datetime import UTC, datetime, timedelta
from uuid import UUID

import redis.asyncio as redis
from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import (
    DriverLocation,
    IncidentCreate,
    IncidentOut,
    LatLng,
    SharedRideView,
    ShareTokenOut,
)
from app.core.config import settings
from app.core.ratelimit import limiter
from app.core.security import current_user_id
from app.db.models import Booking, BookingStatus, Incident, Ride, ShareToken, User
from app.db.session import get_session
from app.services import notifications as notify
from app.services.geo import lonlat_from_wkb

log = logging.getLogger(__name__)
router = APIRouter()


# ---------- Trip share ---------- #


def _share_expiry(ride: Ride) -> datetime:
    """Share tokens live for ride_duration_estimate + 30 min. We have no
    ETA service yet, so cap at 4 hours past depart for now."""
    base = max(datetime.now(UTC), ride.depart_at) + timedelta(hours=4)
    return base


@router.post(
    "/rides/{ride_id}/share",
    response_model=ShareTokenOut,
    status_code=status.HTTP_201_CREATED,
    summary="Generate a public share-link token for an active or scheduled ride",
)
async def create_share_token(
    ride_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> ShareTokenOut:
    ride = await session.get(Ride, ride_id)
    if ride is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    # Only participants (driver or a booked rider) can create a share link.
    if ride.driver_id != user_id:
        booked = (
            await session.execute(
                select(Booking).where(
                    Booking.ride_id == ride_id,
                    Booking.rider_id == user_id,
                    Booking.status != BookingStatus.cancelled,
                )
            )
        ).scalar_one_or_none()
        if booked is None:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN)

    token = secrets.token_urlsafe(32)[:64]
    expires_at = _share_expiry(ride)
    session.add(ShareToken(token=token, ride_id=ride_id, expires_at=expires_at))
    await session.commit()
    return ShareTokenOut(token=token, expires_at=expires_at)


@router.get(
    "/share/{token}",
    response_model=SharedRideView,
    summary="Public read of a ride via a previously generated share token",
)
async def read_shared_ride(
    token: str,
    session: AsyncSession = Depends(get_session),
) -> SharedRideView:
    row = await session.get(ShareToken, token)
    if row is None or row.expires_at < datetime.now(UTC):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)

    ride = await session.get(Ride, row.ride_id)
    if ride is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)

    o_lat, o_lng = lonlat_from_wkb(ride.origin)
    d_lat, d_lng = lonlat_from_wkb(ride.destination)

    last_known: DriverLocation | None = None
    r = redis.from_url(settings.redis_url, decode_responses=True)
    try:
        raw = await r.get(f"ride:{ride.id}:loc")
        if raw:
            last_known = DriverLocation(**json.loads(raw))
    finally:
        await r.aclose()

    return SharedRideView(
        ride_id=ride.id,
        status=ride.status.value,
        origin_label=ride.origin_label,
        destination_label=ride.destination_label,
        origin=LatLng(lat=o_lat, lng=o_lng),
        destination=LatLng(lat=d_lat, lng=d_lng),
        driver_name=ride.driver.name,
        driver_rating_avg=float(ride.driver.rating_avg or 0),
        last_known=last_known,
        expires_at=row.expires_at,
    )


# ---------- Incidents ---------- #


@router.post(
    "/rides/{ride_id}/incidents",
    response_model=IncidentOut,
    status_code=status.HTTP_201_CREATED,
)
@limiter.limit("10/minute")
async def report_incident(
    request: Request,
    response: Response,
    ride_id: UUID,
    body: IncidentCreate,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> IncidentOut:
    """Record an incident on a ride. Anyone with access to the ride can
    report. We also push a notification to the reporter's trusted
    contacts who happen to be Route Mates users (best-effort)."""
    ride = await session.get(Ride, ride_id)
    if ride is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    if ride.driver_id != user_id:
        booked = (
            await session.execute(
                select(Booking).where(
                    Booking.ride_id == ride_id,
                    Booking.rider_id == user_id,
                    Booking.status != BookingStatus.cancelled,
                )
            )
        ).scalar_one_or_none()
        if booked is None:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN)

    incident = Incident(
        ride_id=ride_id,
        reporter_id=user_id,
        kind=body.kind,
        description=body.description,
        lat=body.lat,
        lng=body.lng,
    )
    session.add(incident)
    await session.commit()
    await session.refresh(incident)

    # Best-effort fan-out to any trusted contact whose phone matches a
    # Route Mates user. Real SMS / WhatsApp delivery is deferred to a
    # provider integration (Phase 7+).
    reporter = await session.get(User, user_id)
    if reporter and reporter.trusted_contacts:
        phones = [c.get("phone") for c in reporter.trusted_contacts if c.get("phone")]
        if phones:
            matched = (
                await session.execute(
                    select(User.id).where(User.phone.in_(phones))
                )
            ).scalars().all()
            for contact_id in matched:
                await notify.notify_user(
                    session,
                    contact_id,
                    kind="safety.incident",
                    title="Safety alert from Route Mates",
                    body=(
                        f"{reporter.name or 'A contact'} reported an incident — "
                        "open the share link."
                    ),
                    data={"ride_id": str(ride_id), "incident_id": str(incident.id)},
                )

    return IncidentOut.model_validate(incident)


# ---------- Block / unblock ---------- #


@router.post("/users/{target_id}/block", status_code=status.HTTP_204_NO_CONTENT)
async def block_user(
    target_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> None:
    if target_id == user_id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="cannot block self")
    me = await session.get(User, user_id)
    if me is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    current = list(me.blocked_user_ids or [])
    if target_id not in current:
        current.append(target_id)
        me.blocked_user_ids = current
        await session.commit()


@router.delete("/users/{target_id}/block", status_code=status.HTTP_204_NO_CONTENT)
async def unblock_user(
    target_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> None:
    me = await session.get(User, user_id)
    if me is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    current = list(me.blocked_user_ids or [])
    new = [x for x in current if x != target_id]
    if new != current:
        me.blocked_user_ids = new
        await session.commit()
