import json
from uuid import UUID

import redis.asyncio as redis
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import (
    BookingOut,
    DriverLocation,
    MeOut,
    MePatch,
    RatingPrompt,
    RideBooking,
    TripsOut,
    UserOut,
    UserSettingsOut,
    UserSettingsPatch,
)
from app.core.config import settings
from app.core.security import current_user_id
from app.db.models import Booking, BookingStatus, Rating, Ride, RideStatus, User, UserSettings
from app.db.session import get_session
from app.services.rides import ride_to_out, seats_taken

router = APIRouter()


@router.get("", response_model=MeOut)
async def get_me(
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> MeOut:
    user = await session.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    return MeOut.model_validate(user)


@router.patch("", response_model=MeOut)
async def patch_me(
    body: MePatch,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> MeOut:
    user = await session.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    if body.name is not None:
        user.name = body.name
    if body.photo_url is not None:
        user.photo_url = body.photo_url
    if body.upi_id is not None:
        user.upi_id = body.upi_id.strip() or None
    await session.commit()
    await session.refresh(user)
    return MeOut.model_validate(user)


@router.get("/trips", response_model=TripsOut)
async def my_trips(
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> TripsOut:
    """Aggregated upcoming/active trips for the current user.

    `driving` includes rides where the user is the driver and the ride is
    `scheduled` or `started`. `riding` includes bookings the user holds where
    the underlying ride is still active and the booking isn't `cancelled` or
    `rejected`.
    """
    driving_rides = (
        await session.execute(
            select(Ride)
            .where(
                Ride.driver_id == user_id,
                Ride.status.in_({RideStatus.scheduled, RideStatus.started}),
            )
            .order_by(Ride.depart_at.asc())
        )
    ).scalars().all()
    driving = [ride_to_out(r, await seats_taken(session, r.id)) for r in driving_rides]

    bookings = (
        await session.execute(
            select(Booking, Ride)
            .join(Ride, Ride.id == Booking.ride_id)
            .where(
                Booking.rider_id == user_id,
                Booking.status.notin_({BookingStatus.cancelled, BookingStatus.rejected}),
                Ride.status.in_({RideStatus.scheduled, RideStatus.started}),
            )
            .order_by(Ride.depart_at.asc())
        )
    ).all()

    riding: list[RideBooking] = []
    for booking, ride in bookings:
        riding.append(
            RideBooking(
                booking=BookingOut.model_validate(booking),
                ride=ride_to_out(ride, await seats_taken(session, ride.id)),
            )
        )

    # Rides this user has ridden on (booking ever accepted) that are
    # completed AND not yet rated by the user.
    awaiting_rows = (
        await session.execute(
            select(Booking, Ride)
            .join(Ride, Ride.id == Booking.ride_id)
            .where(
                Booking.rider_id == user_id,
                Booking.status == BookingStatus.accepted,
                Ride.status == RideStatus.completed,
                ~select(Rating.id)
                .where(Rating.ride_id == Ride.id, Rating.from_id == user_id)
                .exists(),
            )
            .order_by(Ride.depart_at.desc())
            .limit(20)
        )
    ).all()
    awaiting_rating: list[RideBooking] = []
    for booking, ride in awaiting_rows:
        awaiting_rating.append(
            RideBooking(
                booking=BookingOut.model_validate(booking),
                ride=ride_to_out(ride, await seats_taken(session, ride.id)),
            )
        )

    # Drivers rate accepted riders. One row per (ride, rider) pair where the
    # ride is completed and I haven't rated that rider yet.
    driver_prompt_rows = (
        await session.execute(
            select(Booking, Ride, User)
            .join(Ride, Ride.id == Booking.ride_id)
            .join(User, User.id == Booking.rider_id)
            .where(
                Ride.driver_id == user_id,
                Ride.status == RideStatus.completed,
                Booking.status == BookingStatus.accepted,
                ~select(Rating.id)
                .where(
                    Rating.ride_id == Ride.id,
                    Rating.from_id == user_id,
                    Rating.to_id == Booking.rider_id,
                )
                .exists(),
            )
            .order_by(Ride.depart_at.desc())
            .limit(50)
        )
    ).all()
    awaiting_driver_rating: list[RatingPrompt] = []
    for _booking, ride, rider in driver_prompt_rows:
        awaiting_driver_rating.append(
            RatingPrompt(
                ride=ride_to_out(ride, await seats_taken(session, ride.id)),
                target=UserOut.model_validate(rider),
            )
        )

    return TripsOut(
        driving=driving,
        riding=riding,
        awaiting_rating=awaiting_rating,
        awaiting_driver_rating=awaiting_driver_rating,
    )


@router.get("/settings", response_model=UserSettingsOut)
async def get_settings(
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> UserSettingsOut:
    row = (
        await session.execute(
            select(UserSettings).where(UserSettings.user_id == user_id)
        )
    ).scalar_one_or_none()
    if row is None:
        return UserSettingsOut()
    return UserSettingsOut.model_validate(row)


@router.patch("/settings", response_model=UserSettingsOut)
async def patch_settings(
    body: UserSettingsPatch,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> UserSettingsOut:
    row = (
        await session.execute(
            select(UserSettings).where(UserSettings.user_id == user_id)
        )
    ).scalar_one_or_none()
    if row is None:
        row = UserSettings(user_id=user_id, muted_kinds=[])
        session.add(row)

    fields = body.model_fields_set
    if "quiet_start_hour" in fields:
        row.quiet_start_hour = body.quiet_start_hour
    if "quiet_end_hour" in fields:
        row.quiet_end_hour = body.quiet_end_hour
    if "muted_kinds" in fields and body.muted_kinds is not None:
        row.muted_kinds = body.muted_kinds

    await session.commit()
    await session.refresh(row)
    return UserSettingsOut.model_validate(row)


@router.get(
    "/rides/{ride_id}/location",
    response_model=DriverLocation,
    summary="Last-known driver location for an active ride",
)
async def ride_location(
    ride_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> DriverLocation:
    """Returns the most recent driver location pushed via the WebSocket.

    Authorisation: caller must be the driver or hold a non-cancelled booking
    on the ride. Used by the rider's RideDetail screen to seed the map before
    the WS reconnects.
    """
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

    r = redis.from_url(settings.redis_url, decode_responses=True)
    try:
        raw = await r.get(f"ride:{ride_id}:loc")
    finally:
        await r.aclose()

    if raw is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="no location yet")
    return DriverLocation(**json.loads(raw))
