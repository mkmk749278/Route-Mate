from datetime import UTC, datetime, timedelta
from uuid import UUID

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt

from app.core.config import settings
from app.core.logging import user_id_var

_bearer = HTTPBearer(auto_error=False)


def create_app_jwt(user_id: UUID) -> str:
    now = datetime.now(UTC)
    payload = {
        "sub": str(user_id),
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(hours=settings.jwt_ttl_hours)).timestamp()),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def decode_app_jwt(token: str) -> UUID:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
        return UUID(payload["sub"])
    except (JWTError, KeyError, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid token"
        ) from exc


async def current_user_id(
    creds: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> UUID:
    if creds is None or creds.scheme.lower() != "bearer":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="missing token")
    uid = decode_app_jwt(creds.credentials)
    # Stamp the access log + any handler logs with the authenticated user.
    user_id_var.set(str(uid))
    return uid
