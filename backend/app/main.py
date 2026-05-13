from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import auth, bookings, devices, geocode, me, ratings, rides
from app.core.config import settings
from app.core.firebase import init_firebase
from app.core.logging import RequestContextMiddleware, configure_logging
from app.db.session import dispose_engine
from app.ws.ride import router as ws_router


@asynccontextmanager
async def lifespan(_: FastAPI):
    configure_logging()
    init_firebase()
    yield
    await dispose_engine()


app = FastAPI(title="Route Mates API", version="0.1.0", lifespan=lifespan)

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


app.include_router(auth.router, prefix="/v1/auth", tags=["auth"])
app.include_router(me.router, prefix="/v1/me", tags=["me"])
app.include_router(rides.router, prefix="/v1/rides", tags=["rides"])
app.include_router(bookings.router, prefix="/v1/bookings", tags=["bookings"])
app.include_router(ratings.router, prefix="/v1/rides", tags=["ratings"])
app.include_router(devices.router, prefix="/v1/devices", tags=["devices"])
app.include_router(geocode.router, prefix="/v1/geocode", tags=["geocode"])
app.include_router(ws_router, prefix="/v1/ws", tags=["ws"])
