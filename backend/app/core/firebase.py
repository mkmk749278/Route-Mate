import logging
from pathlib import Path

import firebase_admin
from firebase_admin import auth as fb_auth
from firebase_admin import credentials, messaging

from app.core.config import settings

log = logging.getLogger(__name__)
_app: firebase_admin.App | None = None


def init_firebase() -> None:
    global _app
    if _app is not None:
        return

    path = settings.firebase_credentials_path
    if not path:
        log.warning("FIREBASE_CREDENTIALS_PATH not set; Firebase disabled (dev only)")
        return

    p = Path(path)
    if not p.exists() or p.stat().st_size == 0:
        log.warning(
            "Firebase credentials file %s missing or empty; Firebase disabled. "
            "POST /v1/auth/exchange will return 401 until you place the Admin SDK JSON.",
            path,
        )
        return

    try:
        cred = credentials.Certificate(path)
        _app = firebase_admin.initialize_app(cred)
        log.info("Firebase Admin SDK initialised")
    except Exception:  # noqa: BLE001
        log.exception("Failed to initialise Firebase Admin SDK; continuing without it")


def verify_firebase_id_token(id_token: str) -> dict:
    if _app is None:
        raise RuntimeError("firebase not initialised")
    return fb_auth.verify_id_token(id_token)


def send_fcm(token: str, title: str, body: str, data: dict[str, str] | None = None) -> None:
    if _app is None:
        log.warning("FCM skipped (firebase not initialised)")
        return
    msg = messaging.Message(
        token=token,
        notification=messaging.Notification(title=title, body=body),
        data=data or {},
    )
    try:
        messaging.send(msg)
    except Exception:  # noqa: BLE001
        log.exception("FCM send failed")
