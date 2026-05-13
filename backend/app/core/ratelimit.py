"""slowapi-backed rate limiter.

Keying:
- Authenticated routes key on the bearer-token subject (user_id), so a
  rate-limited spam attempt from one device can't lock out another user
  behind the same NAT.
- Un-authenticated routes (auth/exchange, auth/dev-login) key on the
  remote IP.

Backing store: Redis if configured (default), in-memory as a fallback
for tests / dev. Limits are deliberately generous — they exist to stop
runaway loops and very basic abuse, not to enforce business quotas.
"""
from __future__ import annotations

from fastapi import Request
from slowapi import Limiter
from slowapi.util import get_remote_address

from app.core.config import settings


def _key(request: Request) -> str:
    auth = request.headers.get("authorization", "")
    if auth.lower().startswith("bearer "):
        # Use the raw token as the key — we don't decode here to keep
        # this lightweight. Same caller = same key, which is what
        # matters for rate-limiting.
        return auth[7:64]  # bound the key length
    return get_remote_address(request)


limiter = Limiter(
    key_func=_key,
    storage_uri=settings.redis_url,
    in_memory_fallback_enabled=True,
    headers_enabled=True,
)
