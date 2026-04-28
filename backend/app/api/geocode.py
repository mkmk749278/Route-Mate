from uuid import UUID

from fastapi import APIRouter, Depends, Query

from app.core.security import current_user_id
from app.services.geocode import GeocodeHit, geocode

router = APIRouter()


@router.get("", response_model=list[GeocodeHit])
async def geocode_search(
    q: str = Query(..., min_length=3, max_length=120),
    limit: int = Query(default=5, ge=1, le=10),
    _: UUID = Depends(current_user_id),
) -> list[GeocodeHit]:
    return await geocode(q, limit=limit)
