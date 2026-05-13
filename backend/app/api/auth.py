from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import AuthExchange, AuthResult, MeOut
from app.core.config import settings
from app.core.firebase import verify_firebase_id_token
from app.core.ratelimit import limiter
from app.core.security import create_app_jwt
from app.db.models import User
from app.db.session import get_session

router = APIRouter()


class DevLoginRequest(BaseModel):
    phone: str = Field(..., min_length=4, max_length=32)
    name: str | None = None


@router.post("/dev-login", response_model=AuthResult)
@limiter.limit("10/minute")
async def dev_login(
    request: Request,
    response: Response,
    body: DevLoginRequest,
    session: AsyncSession = Depends(get_session),
) -> AuthResult:
    """Pre-Firebase shortcut: accept any phone, upsert a user, return a JWT.

    Disabled in production by leaving DEV_LOGIN_ENABLED=false. Used for v0.1
    while we ship before wiring Firebase Phone Auth.
    """
    if not settings.dev_login_enabled:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail="dev login disabled"
        )

    fake_uid = f"dev:{body.phone}"
    user = (
        await session.execute(select(User).where(User.firebase_uid == fake_uid))
    ).scalar_one_or_none()
    if user is None:
        user = User(firebase_uid=fake_uid, phone=body.phone, name=body.name)
        session.add(user)
        await session.commit()
        await session.refresh(user)
    elif body.name and user.name != body.name:
        user.name = body.name
        await session.commit()
        await session.refresh(user)

    return AuthResult(token=create_app_jwt(user.id), user=MeOut.model_validate(user))


@router.post("/exchange", response_model=AuthResult)
@limiter.limit("10/minute")
async def exchange(
    request: Request,
    response: Response,
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
