import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware

from app.api import (
    auth,
    bookings,
    devices,
    geocode,
    me,
    ratings,
    rides,
    safety,
    saved_routes,
)
from app.core.config import settings
from app.core.firebase import init_firebase
from app.core.logging import RequestContextMiddleware, configure_logging
from app.core.metrics import PrometheusMiddleware, metrics_response
from app.core.ratelimit import limiter
from app.db.session import dispose_engine
from app.services import anomaly
from app.ws.ride import router as ws_router


@asynccontextmanager
async def lifespan(_: FastAPI):
    configure_logging()
    init_firebase()
    task = asyncio.create_task(anomaly.run_loop(), name="anomaly-loop")
    try:
        yield
    finally:
        task.cancel()
        try:
            await task
        except (asyncio.CancelledError, Exception):  # noqa: BLE001
            pass
        await dispose_engine()


app = FastAPI(title="Route Mates API", version="0.1.0", lifespan=lifespan)

app.state.limiter = limiter
app.add_exception_handler(
    RateLimitExceeded,
    lambda request, exc: __import__("fastapi").responses.JSONResponse(  # type: ignore[no-any-return]
        status_code=429, content={"detail": "rate limit exceeded"}
    ),
)

app.add_middleware(SlowAPIMiddleware)
app.add_middleware(PrometheusMiddleware)
app.add_middleware(RequestContextMiddleware)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/v1/health")
async def health() -> dict:
    return {"ok": True, "service": "routemate-api"}


@app.get("/metrics", include_in_schema=False)
async def metrics():  # type: ignore[no-untyped-def]
    return metrics_response()


app.include_router(auth.router, prefix="/v1/auth", tags=["auth"])
app.include_router(me.router, prefix="/v1/me", tags=["me"])
app.include_router(rides.router, prefix="/v1/rides", tags=["rides"])
app.include_router(bookings.router, prefix="/v1/bookings", tags=["bookings"])
app.include_router(ratings.router, prefix="/v1/rides", tags=["ratings"])
app.include_router(devices.router, prefix="/v1/devices", tags=["devices"])
app.include_router(geocode.router, prefix="/v1/geocode", tags=["geocode"])
app.include_router(safety.router, prefix="/v1", tags=["safety"])
app.include_router(
    saved_routes.router, prefix="/v1/me/saved-routes", tags=["saved-routes"]
)
app.include_router(ws_router, prefix="/v1/ws", tags=["ws"])
