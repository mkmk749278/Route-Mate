from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import AuthExchange, AuthResult, MeOut
from app.core.firebase import verify_firebase_id_token
from app.core.security import create_app_jwt
from app.db.models import User
from app.db.session import get_session

router = APIRouter()


@router.post("/exchange", response_model=AuthResult)
async def exchange(
    body: AuthExchange,
    session: AsyncSession = Depends(get_session),
) -> AuthResult:
    try:
        decoded = verify_firebase_id_token(body.id_token)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid firebase token"
        ) from exc

    uid: str = decoded["uid"]
    phone: str | None = decoded.get("phone_number")

    user = (
        await session.execute(select(User).where(User.firebase_uid == uid))
    ).scalar_one_or_none()
    if user is None:
        user = User(firebase_uid=uid, phone=phone)
        session.add(user)
        await session.commit()
        await session.refresh(user)
    elif phone and user.phone != phone:
        user.phone = phone
        await session.commit()

    return AuthResult(token=create_app_jwt(user.id), user=MeOut.model_validate(user))
