from uuid import UUID

from fastapi import APIRouter, Depends
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import FcmRegister
from app.core.security import current_user_id
from app.db.models import FcmToken
from app.db.session import get_session

router = APIRouter()


@router.post("/fcm")
async def register_fcm(
    body: FcmRegister,
    user_id: UUID = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
) -> dict:
    stmt = (
        insert(FcmToken)
        .values(token=body.token, user_id=user_id, platform=body.platform)
        .on_conflict_do_update(
            index_elements=[FcmToken.token],
            set_={"user_id": user_id, "platform": body.platform},
        )
    )
    await session.execute(stmt)
    await session.commit()
    return {"ok": True}
