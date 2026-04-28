from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import BookingOut
from app.core.security import current_user_id
from app.db.models import Booking, BookingStatus, Ride
from app.db.session import get_session

router = APIRouter()


@router.get("/me", response_model=list[BookingOut])
async def list_my_bookings(
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> list[BookingOut]:
    rows = (
        await session.execute(
            select(Booking).where(Booking.rider_id == user_id).order_by(Booking.created_at.desc())
        )
    ).scalars().all()
    return [BookingOut.model_validate(b) for b in rows]


async def _transition(
    session: AsyncSession,
    booking_id: UUID,
    actor: UUID,
    new_status: BookingStatus,
    *,
    actor_must_be_driver: bool,
) -> Booking:
    booking = await session.get(Booking, booking_id)
    if booking is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    ride = await session.get(Ride, booking.ride_id)
    if ride is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    if actor_must_be_driver and ride.driver_id != actor:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN)
    if not actor_must_be_driver and booking.rider_id != actor:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN)
    booking.status = new_status
    await session.commit()
    await session.refresh(booking)
    return booking


@router.post("/{booking_id}/accept", response_model=BookingOut)
async def accept(
    booking_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> BookingOut:
    b = await _transition(
        session, booking_id, user_id, BookingStatus.accepted, actor_must_be_driver=True
    )
    return BookingOut.model_validate(b)


@router.post("/{booking_id}/reject", response_model=BookingOut)
async def reject(
    booking_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> BookingOut:
    b = await _transition(
        session, booking_id, user_id, BookingStatus.rejected, actor_must_be_driver=True
    )
    return BookingOut.model_validate(b)


@router.post("/{booking_id}/cancel", response_model=BookingOut)
async def cancel(
    booking_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> BookingOut:
    b = await _transition(
        session, booking_id, user_id, BookingStatus.cancelled, actor_must_be_driver=False
    )
    return BookingOut.model_validate(b)
