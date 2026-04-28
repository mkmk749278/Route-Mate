from datetime import datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    name: str | None = None
    photo_url: str | None = None
    rating_avg: float = 0
    rating_count: int = 0


class MeOut(UserOut):
    phone: str | None = None


class MePatch(BaseModel):
    name: str | None = None
    photo_url: str | None = None


class AuthExchange(BaseModel):
    id_token: str


class AuthResult(BaseModel):
    token: str
    user: MeOut


class LatLng(BaseModel):
    lat: float = Field(..., ge=-90, le=90)
    lng: float = Field(..., ge=-180, le=180)


class RideCreate(BaseModel):
    origin: LatLng
    destination: LatLng
    origin_label: str
    destination_label: str
    polyline: str | None = None
    depart_at: datetime
    seats_total: int = Field(..., ge=1, le=8)
    price_per_seat: Decimal = Field(..., ge=0)


class RideOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    driver: UserOut
    origin: LatLng
    destination: LatLng
    origin_label: str
    destination_label: str
    depart_at: datetime
    seats_total: int
    seats_available: int
    price_per_seat: Decimal
    status: str
    polyline: str | None = None


class BookingCreate(BaseModel):
    seats: int = Field(default=1, ge=1, le=8)


class BookingOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    ride_id: UUID
    rider: UserOut
    seats: int
    status: str
    created_at: datetime


class RatingCreate(BaseModel):
    to_user_id: UUID
    stars: int = Field(..., ge=1, le=5)
    text: str | None = None


class FcmRegister(BaseModel):
    token: str
    platform: str = "android"


class MessageOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    ride_id: UUID
    sender_id: UUID
    body: str
    created_at: datetime
