"""Outbound FCM notifications, grouped by `kind` for client-side filtering.

`notify_user(...)` is the only entry point used by routers. It fans out
to every registered FCM token for that user, applies per-user mute
preferences (quiet hours + muted kinds), and prunes unregistered tokens
on send failure. The function is best-effort: a failure here must never
fail the originating request.

The `data` payload is intentionally string-keyed string-valued — FCM
data messages can only carry strings, and the Android side parses
them in RouteMatesMessagingService.
"""
from __future__ import annotations

import logging
from datetime import UTC, datetime, timedelta, timezone
from uuid import UUID

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.firebase import send_fcm
from app.db.models import FcmToken, UserSettings

log = logging.getLogger(__name__)

# Notification kinds. Clients (and per-user mute lists) reference these
# by string so we don't have to bump the API when a new event is added.
KIND_BOOKING_CREATED = "booking.created"
KIND_BOOKING_ACCEPTED = "booking.accepted"
KIND_BOOKING_REJECTED = "booking.rejected"
KIND_BOOKING_CANCELLED = "booking.cancelled"
KIND_RIDE_STARTED = "ride.started"
KIND_RIDE_CANCELLED = "ride.cancelled"
KIND_RIDE_COMPLETED = "ride.completed"
KIND_CHAT = "chat.message"

# India-centric: assume IST for quiet-hours interpretation. If the user
# population diversifies, store the user's tz on `users` and look it up.
_IST = timezone(timedelta(hours=5, minutes=30))


def _in_quiet_window(now_hour: int, start: int | None, end: int | None) -> bool:
    if start is None or end is None:
        return False
    if start == end:
        return False
    # Window may wrap midnight (e.g. 22 → 7).
    if start < end:
        return start <= now_hour < end
    return now_hour >= start or now_hour < end


async def _user_settings(session: AsyncSession, user_id: UUID) -> UserSettings | None:
    return (
        await session.execute(
            select(UserSettings).where(UserSettings.user_id == user_id)
        )
    ).scalar_one_or_none()


async def notify_user(
    session: AsyncSession,
    user_id: UUID,
    *,
    kind: str,
    title: str,
    body: str,
    data: dict[str, str] | None = None,
) -> None:
    """Best-effort: send `kind` to every FCM token registered for user_id.

    Silently drops the send when (a) the user has the kind muted, (b) we
    are inside the user's quiet hours, or (c) Firebase isn't configured
    on this deployment.
    """
    settings = await _user_settings(session, user_id)
    if settings is not None:
        if kind in (settings.muted_kinds or []):
            return
        if _in_quiet_window(
            datetime.now(UTC).astimezone(_IST).hour,
            settings.quiet_start_hour,
            settings.quiet_end_hour,
        ):
            return

    tokens = (
        await session.execute(
            select(FcmToken.token).where(FcmToken.user_id == user_id)
        )
    ).scalars().all()
    if not tokens:
        return

    payload: dict[str, str] = {"kind": kind}
    if data:
        payload.update({k: str(v) for k, v in data.items()})

    dead: list[str] = []
    for tk in tokens:
        try:
            send_fcm(tk, title, body, payload)
        except Exception as exc:  # noqa: BLE001
            log.warning("fcm send failed for %s: %s", tk[:8], exc)
            # Prune obviously dead tokens. The Admin SDK raises
            # firebase_admin.messaging.UnregisteredError on revoked tokens.
            if "Unregistered" in type(exc).__name__ or "InvalidArgument" in type(exc).__name__:
                dead.append(tk)

    if dead:
        await session.execute(delete(FcmToken).where(FcmToken.token.in_(dead)))
        await session.commit()
