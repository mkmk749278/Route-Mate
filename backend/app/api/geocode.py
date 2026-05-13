from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response, status

from app.core.ratelimit import limiter
from app.core.security import current_user_id
from app.services.geocode import GeocodeHit, geocode, reverse

router = APIRouter()


@router.get("", response_model=list[GeocodeHit])
@limiter.limit("30/minute")
async def geocode_search(
    request: Request,
    response: Response,
    q: str = Query(..., min_length=3, max_length=120),
    limit: int = Query(default=5, ge=1, le=10),
    _: UUID = Depends(current_user_id),
) -> list[GeocodeHit]:
    return await geocode(q, limit=limit)


@router.get("/reverse", response_model=GeocodeHit)
@limiter.limit("30/minute")
async def geocode_reverse(
    request: Request,
    response: Response,
    lat: float = Query(..., ge=-90, le=90),
    lng: float = Query(..., ge=-180, le=180),
    _: UUID = Depends(current_user_id),
) -> GeocodeHit:
    hit = await reverse(lat, lng)
    if hit is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="no result")
    return hit
