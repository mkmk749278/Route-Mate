"""Phase 10 driver shortcut: 'Home → Office' style saved routes.

CRUD-only; the Offer screen on Android can later prefill its fields by
selecting one of these. Hosting them on the server (rather than locally
in Room) means a user who reinstalls or signs in from a fresh device
gets their shortcuts back.
"""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import LatLng, SavedRouteIn, SavedRouteOut
from app.core.security import current_user_id
from app.db.models import SavedRoute
from app.db.session import get_session

router = APIRouter()


def _to_out(r: SavedRoute) -> SavedRouteOut:
    return SavedRouteOut(
        id=r.id,
        name=r.name,
        origin=LatLng(lat=float(r.origin_lat), lng=float(r.origin_lng)),
        destination=LatLng(lat=float(r.destination_lat), lng=float(r.destination_lng)),
        origin_label=r.origin_label,
        destination_label=r.destination_label,
        recurrence_days=r.recurrence_days,
    )


@router.get("", response_model=list[SavedRouteOut])
async def list_saved_routes(
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> list[SavedRouteOut]:
    rows = (
        await session.execute(
            select(SavedRoute)
            .where(SavedRoute.user_id == user_id)
            .order_by(SavedRoute.created_at.asc())
        )
    ).scalars().all()
    return [_to_out(r) for r in rows]


@router.post("", response_model=SavedRouteOut, status_code=status.HTTP_201_CREATED)
async def create_saved_route(
    body: SavedRouteIn,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> SavedRouteOut:
    existing = (
        await session.execute(
            select(SavedRoute).where(
                SavedRoute.user_id == user_id, SavedRoute.name == body.name
            )
        )
    ).scalar_one_or_none()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="name already used"
        )

    row = SavedRoute(
        user_id=user_id,
        name=body.name,
        origin_lat=body.origin.lat,
        origin_lng=body.origin.lng,
        destination_lat=body.destination.lat,
        destination_lng=body.destination.lng,
        origin_label=body.origin_label,
        destination_label=body.destination_label,
        recurrence_days=body.recurrence_days,
    )
    session.add(row)
    await session.commit()
    await session.refresh(row)
    return _to_out(row)


@router.delete("/{route_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_saved_route(
    route_id: UUID,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> None:
    row = await session.get(SavedRoute, route_id)
    if row is None or row.user_id != user_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    await session.delete(row)
    await session.commit()
