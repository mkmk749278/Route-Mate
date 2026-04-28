from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import RatingCreate
from app.core.security import current_user_id
from app.db.models import Booking, Rating, Ride, RideStatus, User
from app.db.session import get_session

router = APIRouter()


@router.post("/{ride_id}/ratings", status_code=status.HTTP_201_CREATED)
async def rate(
    ride_id: UUID,
    body: RatingCreate,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> dict:
    ride = await session.get(Ride, ride_id)
    if ride is None or ride.status != RideStatus.completed:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="ride not completed")

    is_driver = ride.driver_id == user_id
    is_rider = (
        await session.execute(
            select(Booking).where(Booking.ride_id == ride_id, Booking.rider_id == user_id)
        )
    ).scalar_one_or_none() is not None
    if not (is_driver or is_rider):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN)

    rating = Rating(
        ride_id=ride_id,
        from_id=user_id,
        to_id=body.to_user_id,
        stars=body.stars,
        text=body.text,
    )
    session.add(rating)
    await session.flush()

    avg, count = (
        await session.execute(
            select(func.avg(Rating.stars), func.count(Rating.id)).where(
                Rating.to_id == body.to_user_id
            )
        )
    ).one()
    target = await session.get(User, body.to_user_id)
    if target is not None:
        target.rating_avg = float(avg or 0)
        target.rating_count = int(count or 0)

    await session.commit()
    return {"ok": True}
