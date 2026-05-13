from datetime import datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    name: str | None = None
    photo_url: str | None = None
    upi_id: str | None = None
    rating_avg: float = 0
    rating_count: int = 0


class TrustedContact(BaseModel):
    name: str = Field(..., min_length=1, max_length=120)
    phone: str = Field(..., min_length=4, max_length=32)


class MeOut(UserOut):
    phone: str | None = None
    trusted_contacts: list[TrustedContact] = []


class MePatch(BaseModel):
    name: str | None = None
    photo_url: str | None = None
    upi_id: str | None = None
    trusted_contacts: list[TrustedContact] | None = None


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
    recurrence_days: int = Field(default=0, ge=0, le=127)


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
    recurrence_days: int = 0


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


class RideBooking(BaseModel):
    booking: BookingOut
    ride: RideOut


class RatingOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    ride_id: UUID
    from_id: UUID
    to_id: UUID
    stars: int
    text: str | None = None


class RatingPrompt(BaseModel):
    """Tells the client: 'rate this user on this ride'.

    Used for driver-rates-rider direction where one ride may have multiple
    targets; rider-rates-driver continues to use awaiting_rating: list[RideBooking]
    since the target is implicit (ride.driver).
    """
    ride: RideOut
    target: UserOut


class TripsOut(BaseModel):
    """Aggregated trips for the current user, both as driver and rider."""
    driving: list[RideOut]
    riding: list[RideBooking]
    awaiting_rating: list[RideBooking]
    awaiting_driver_rating: list[RatingPrompt] = []


class DriverLocation(BaseModel):
    lat: float
    lng: float
    ts: int


class MessageOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    ride_id: UUID
    sender_id: UUID
    body: str
    created_at: datetime


class IncidentCreate(BaseModel):
    kind: str = Field(..., min_length=2, max_length=32)
    description: str | None = None
    lat: float | None = Field(default=None, ge=-90, le=90)
    lng: float | None = Field(default=None, ge=-180, le=180)


class IncidentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    ride_id: UUID
    kind: str
    description: str | None = None
    created_at: datetime


class ShareTokenOut(BaseModel):
    token: str
    expires_at: datetime


class SavedRouteIn(BaseModel):
    name: str = Field(..., min_length=1, max_length=120)
    origin: LatLng
    destination: LatLng
    origin_label: str = Field(..., min_length=1, max_length=200)
    destination_label: str = Field(..., min_length=1, max_length=200)
    recurrence_days: int = Field(default=0, ge=0, le=127)


class SavedRouteOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    name: str
    origin: LatLng
    destination: LatLng
    origin_label: str
    destination_label: str
    recurrence_days: int


class SharedRideView(BaseModel):
    """Public payload returned by the share endpoint. Strips PII like phone
    numbers and only exposes what's needed for a trusted contact to watch
    the ride progress."""
    ride_id: UUID
    status: str
    origin_label: str
    destination_label: str
    origin: LatLng
    destination: LatLng
    driver_name: str | None = None
    driver_rating_avg: float = 0
    last_known: DriverLocation | None = None
    expires_at: datetime


class UserSettingsOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    quiet_start_hour: int | None = None
    quiet_end_hour: int | None = None
    muted_kinds: list[str] = []


class UserSettingsPatch(BaseModel):
    quiet_start_hour: int | None = Field(default=None, ge=0, le=23)
    quiet_end_hour: int | None = Field(default=None, ge=0, le=23)
    muted_kinds: list[str] | None = None
    # Sentinel: explicitly clear quiet hours by sending both as null. We
    # distinguish "unset" (field absent) from "set to null" by checking the
    # raw model via model_fields_set.
