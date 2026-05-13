from datetime import datetime, timedelta
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import BookingCreate, BookingOut, MessageOut, RatingOut, RideCreate, RideOut
from app.core.security import current_user_id
from app.db.models import Booking, BookingStatus, Message, Rating, Ride, RideStatus
from app.db.session import get_session
from app.services import notifications as notify
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
        recurrence_days=body.recurrence_days,
    )
    session.add(ride)
    await session.commit()
    await session.refresh(ride)
    return ride_to_out(ride, 0)


def _next_recurring_depart(depart_at: datetime, recurrence_days: int) -> datetime | None:
    """Given a depart timestamp and a 7-bit Mon-Sun mask, return the next
    same-time depart for any matching weekday in the next 1-7 days, or None
    if the mask is empty.
    """
    if recurrence_days == 0:
        return None
    for delta in range(1, 8):
        candidate = depart_at + timedelta(days=delta)
        if (recurrence_days >> candidate.weekday()) & 1:
            return candidate
    return None


async def _materialize_next_recurring(session: AsyncSession, ride: Ride) -> None:
    """If `ride` has a recurrence pattern, create the next instance.

    Idempotent: skips if a scheduled clone already exists for the same driver
    at the next depart timestamp (avoids duplicates if complete is called
    twice somehow).
    """
    next_depart = _next_recurring_depart(ride.depart_at, ride.recurrence_days)
    if next_depart is None:
        return

    existing = (
        await session.execute(
            select(Ride.id).where(
                Ride.driver_id == ride.driver_id,
                Ride.depart_at == next_depart,
                Ride.status == RideStatus.scheduled,
                Ride.origin_label == ride.origin_label,
                Ride.destination_label == ride.destination_label,
            )
        )
    ).scalar_one_or_none()
    if existing is not None:
        return

    clone = Ride(
        driver_id=ride.driver_id,
        origin=ride.origin,
        destination=ride.destination,
        origin_label=ride.origin_label,
        destination_label=ride.destination_label,
        polyline=ride.polyline,
        depart_at=next_depart,
        seats_total=ride.seats_total,
        price_per_seat=ride.price_per_seat,
        recurrence_days=ride.recurrence_days,
    )
    session.add(clone)
    await session.flush()


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

    booking = Booking(
        ride_id=ride_id,
        rider_id=user_id,
        seats=body.seats,
        # Instant-book: no driver approval step in v0.4. v0.5+ may flip this
        # to BookingStatus.pending behind a per-ride toggle.
        status=BookingStatus.accepted,
    )
    session.add(booking)
    await session.commit()
    await session.refresh(booking)

    await notify.notify_user(
        session,
        ride.driver_id,
        kind=notify.KIND_BOOKING_CREATED,
        title="New booking",
        body=(
            f"{body.seats} seat(s) booked on your "
            f"{ride.origin_label} → {ride.destination_label} ride."
        ),
        data={"ride_id": str(ride_id), "booking_id": str(booking.id)},
    )
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

    accepted = (
        await session.execute(
            select(Booking.rider_id).where(
                Booking.ride_id == ride_id, Booking.status == BookingStatus.accepted
            )
        )
    ).scalars().all()
    for rider_id in accepted:
        await notify.notify_user(
            session,
            rider_id,
            kind=notify.KIND_RIDE_STARTED,
            title="Your driver is on the way",
            body=f"{ride.origin_label} → {ride.destination_label}",
            data={"ride_id": str(ride_id)},
        )
    return ride_to_out(ride, await seats_taken(session, ride.id))


@router.get("/{ride_id}/messages", response_model=list[MessageOut])
async def list_messages(
    ride_id: UUID,
    after: datetime | None = Query(default=None),
    limit: int = Query(default=200, ge=1, le=500),
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> list[MessageOut]:
    """Chat backfill for the ride. Caller must be the driver or have a
    non-cancelled booking on the ride."""
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

    stmt = select(Message).where(Message.ride_id == ride_id).order_by(Message.created_at.asc())
    if after is not None:
        stmt = stmt.where(Message.created_at > after)
    stmt = stmt.limit(limit)
    rows = (await session.execute(stmt)).scalars().all()
    return [MessageOut.model_validate(m) for m in rows]


@router.get(
    "/{ride_id}/ratings/me",
    response_model=RatingOut | None,
    summary="My rating on this ride (optionally to a specific target), or null",
)
async def my_rating(
    ride_id: UUID,
    target: UUID | None = Query(default=None),
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> RatingOut | None:
    stmt = select(Rating).where(Rating.ride_id == ride_id, Rating.from_id == user_id)
    if target is not None:
        stmt = stmt.where(Rating.to_id == target)
    row = (await session.execute(stmt)).scalar_one_or_none()
    return RatingOut.model_validate(row) if row is not None else None


@router.get(
    "/{ride_id}/booking/me",
    response_model=BookingOut | None,
    summary="My booking on this ride, or null if I don't have one",
)
async def my_booking(
    ride_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> BookingOut | None:
    row = (
        await session.execute(
            select(Booking).where(Booking.ride_id == ride_id, Booking.rider_id == user_id)
        )
    ).scalar_one_or_none()
    return BookingOut.model_validate(row) if row is not None else None


@router.post("/{ride_id}/cancel", response_model=RideOut)
async def cancel_ride(
    ride_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> RideOut:
    """Driver-only cancel. Refuses if the ride has already started or
    completed; on success cascades to mark all live bookings cancelled."""
    ride = await session.get(Ride, ride_id)
    if ride is None or ride.driver_id != user_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    if ride.status not in {RideStatus.scheduled}:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="ride not cancellable"
        )
    ride.status = RideStatus.cancelled
    bookings = (
        await session.execute(
            select(Booking).where(
                Booking.ride_id == ride_id,
                Booking.status.notin_({BookingStatus.cancelled, BookingStatus.rejected}),
            )
        )
    ).scalars().all()
    for b in bookings:
        b.status = BookingStatus.cancelled
    await session.commit()
    await session.refresh(ride)

    for b in bookings:
        await notify.notify_user(
            session,
            b.rider_id,
            kind=notify.KIND_RIDE_CANCELLED,
            title="Ride cancelled",
            body=f"Your driver cancelled the {ride.origin_label} → {ride.destination_label} ride.",
            data={"ride_id": str(ride_id)},
        )
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
    await _materialize_next_recurring(session, ride)
    await session.commit()
    await session.refresh(ride)
    return ride_to_out(ride, await seats_taken(session, ride.id))
