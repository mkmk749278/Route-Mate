from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import MeOut, MePatch
from app.core.security import current_user_id
from app.db.models import User
from app.db.session import get_session

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
    await session.commit()
    await session.refresh(user)
    return MeOut.model_validate(user)
