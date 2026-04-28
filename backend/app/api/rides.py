from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import BookingCreate, BookingOut, RideCreate, RideOut
from app.core.security import current_user_id
from app.db.models import Booking, Ride, RideStatus
from app.db.session import get_session
from app.services.geo import st_point
from app.services.rides import ride_to_out, seats_taken

router = APIRouter()

SEARCH_RADIUS_METERS = 2000


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
    return ride_to_out(ride, 0)


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
        out.append(ride_to_out(r, await seats_taken(session, r.id)))
    return out


@router.get("/{ride_id}", response_model=RideOut)
async def get_ride(
    ride_id: UUID,
    session: AsyncSession = Depends(get_session),
) -> RideOut:
    ride = await session.get(Ride, ride_id)
    if ride is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    return ride_to_out(ride, await seats_taken(session, ride.id))


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
    return ride_to_out(ride, await seats_taken(session, ride.id))


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
    return ride_to_out(ride, await seats_taken(session, ride.id))
