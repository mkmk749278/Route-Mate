from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import BookingCreate, BookingOut, LatLng, RideCreate, RideOut
from app.core.security import current_user_id
from app.db.models import Booking, BookingStatus, Ride, RideStatus
from app.db.session import get_session
from app.services.geo import lonlat_from_wkb, st_point

router = APIRouter()

SEARCH_RADIUS_METERS = 2000


def _to_out(ride: Ride, seats_taken: int) -> RideOut:
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


async def _seats_taken(session: AsyncSession, ride_id: UUID) -> int:
    q = select(func.coalesce(func.sum(Booking.seats), 0)).where(
        Booking.ride_id == ride_id, Booking.status == BookingStatus.accepted
    )
    return int((await session.execute(q)).scalar_one())


@router.post("", response_model=RideOut, status_code=status.HTTP_201_CREATED)
async def create_ride(
    body: RideCreate,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> RideOut:
    ride = Ride(
        driver_id=user_id,
        origin=st_point(body.origin.lat, body.origin.lng),
        destination=st_point(body.destination.lat, body.destination.lng),
        origin_label=body.origin_label,
        destination_label=body.destination_label,
        polyline=body.polyline,
        depart_at=body.depart_at,
        seats_total=body.seats_total,
        price_per_seat=body.price_per_seat,
    )
    session.add(ride)
    await session.commit()
    await session.refresh(ride)
    return _to_out(ride, 0)


@router.get("/search", response_model=list[RideOut])
async def search_rides(
    from_lat: float = Query(...),
    from_lng: float = Query(...),
    to_lat: float = Query(...),
    to_lng: float = Query(...),
    depart_after: datetime | None = None,
    depart_before: datetime | None = None,
    radius_m: int = Query(SEARCH_RADIUS_METERS, ge=100, le=10000),
    session: AsyncSession = Depends(get_session),
) -> list[RideOut]:
    origin_pt = st_point(from_lat, from_lng)
    dest_pt = st_point(to_lat, to_lng)

    stmt = (
        select(Ride)
        .where(
            Ride.status == RideStatus.scheduled,
            func.ST_DWithin(Ride.origin, origin_pt, radius_m),
            func.ST_DWithin(Ride.destination, dest_pt, radius_m),
        )
        .order_by(Ride.depart_at.asc())
        .limit(50)
    )
    if depart_after is not None:
        stmt = stmt.where(Ride.depart_at >= depart_after)
    if depart_before is not None:
        stmt = stmt.where(Ride.depart_at <= depart_before)

    rides = (await session.execute(stmt)).scalars().all()
    out: list[RideOut] = []
    for r in rides:
        out.append(_to_out(r, await _seats_taken(session, r.id)))
    return out


@router.get("/{ride_id}", response_model=RideOut)
async def get_ride(
    ride_id: UUID,
    session: AsyncSession = Depends(get_session),
) -> RideOut:
    ride = await session.get(Ride, ride_id)
    if ride is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    return _to_out(ride, await _seats_taken(session, ride.id))


@router.post(
    "/{ride_id}/bookings",
    response_model=BookingOut,
    status_code=status.HTTP_201_CREATED,
)
async def create_booking(
    ride_id: UUID,
    body: BookingCreate,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> BookingOut:
    ride = await session.get(Ride, ride_id)
    if ride is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ride not found")
    if ride.driver_id == user_id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="driver cannot book")
    if ride.status != RideStatus.scheduled:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="ride not bookable")

    existing = (
        await session.execute(
            select(Booking).where(Booking.ride_id == ride_id, Booking.rider_id == user_id)
        )
    ).scalar_one_or_none()
    if existing is not None:
        return BookingOut.model_validate(existing)

    booking = Booking(ride_id=ride_id, rider_id=user_id, seats=body.seats)
    session.add(booking)
    await session.commit()
    await session.refresh(booking)
    return BookingOut.model_validate(booking)


@router.post("/{ride_id}/start", response_model=RideOut)
async def start_ride(
    ride_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> RideOut:
    ride = await session.get(Ride, ride_id)
    if ride is None or ride.driver_id != user_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    if ride.status != RideStatus.scheduled:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="bad status")
    ride.status = RideStatus.started
    await session.commit()
    await session.refresh(ride)
    return _to_out(ride, await _seats_taken(session, ride.id))


@router.post("/{ride_id}/complete", response_model=RideOut)
async def complete_ride(
    ride_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> RideOut:
    ride = await session.get(Ride, ride_id)
    if ride is None or ride.driver_id != user_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    if ride.status != RideStatus.started:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="bad status")
    ride.status = RideStatus.completed
    await session.commit()
    await session.refresh(ride)
    return _to_out(ride, await _seats_taken(session, ride.id))
