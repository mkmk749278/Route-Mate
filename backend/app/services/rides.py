"""Read-side helpers for the rides domain.

Centralises the WKB→LatLng conversion and the seats-available calculation so
both `rides.py` and `me.py` (the trips endpoint) share one source of truth.
"""
from __future__ import annotations

from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import LatLng, RideOut
from app.db.models import Booking, BookingStatus, Ride
from app.services.geo import lonlat_from_wkb


def ride_to_out(ride: Ride, seats_taken: int) -> RideOut:
    o_lat, o_lng = lonlat_from_wkb(ride.origin)
    d_lat, d_lng = lonlat_from_wkb(ride.destination)
    return RideOut(
        id=ride.id,
        driver=ride.driver,
        origin=LatLng(lat=o_lat, lng=o_lng),
        destination=LatLng(lat=d_lat, lng=d_lng),
        origin_label=ride.origin_label,
        destination_label=ride.destination_label,
        depart_at=ride.depart_at,
        seats_total=ride.seats_total,
        seats_available=max(0, ride.seats_total - seats_taken),
        price_per_seat=ride.price_per_seat,
        status=ride.status.value,
        polyline=ride.polyline,
    )


async def seats_taken(session: AsyncSession, ride_id: UUID) -> int:
    q = select(func.coalesce(func.sum(Booking.seats), 0)).where(
        Booking.ride_id == ride_id, Booking.status == BookingStatus.accepted
    )
    return int((await session.execute(q)).scalar_one())
