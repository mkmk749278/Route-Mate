import enum
from datetime import datetime
from uuid import UUID, uuid4

from geoalchemy2 import Geography
from sqlalchemy import (
    DateTime,
    Enum,
    ForeignKey,
    Integer,
    Numeric,
    String,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.dialects.postgresql import UUID as PgUUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class RideStatus(enum.StrEnum):
    scheduled = "scheduled"
    started = "started"
    completed = "completed"
    cancelled = "cancelled"


class BookingStatus(enum.StrEnum):
    pending = "pending"
    accepted = "accepted"
    rejected = "rejected"
    cancelled = "cancelled"


class User(Base):
    __tablename__ = "users"

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    firebase_uid: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    phone: Mapped[str | None] = mapped_column(String(32), index=True)
    name: Mapped[str | None] = mapped_column(String(120))
    photo_url: Mapped[str | None] = mapped_column(String(500))
    upi_id: Mapped[str | None] = mapped_column(String(64))
    rating_avg: Mapped[float] = mapped_column(Numeric(3, 2), default=0)
    rating_count: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )


class Ride(Base):
    __tablename__ = "rides"

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    driver_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    origin: Mapped[str] = mapped_column(Geography("POINT", srid=4326))
    destination: Mapped[str] = mapped_column(Geography("POINT", srid=4326))
    origin_label: Mapped[str] = mapped_column(String(200))
    destination_label: Mapped[str] = mapped_column(String(200))
    polyline: Mapped[str | None] = mapped_column(Text)
    depart_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    seats_total: Mapped[int] = mapped_column(Integer)
    price_per_seat: Mapped[float] = mapped_column(Numeric(8, 2))
    recurrence_days: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
    status: Mapped[RideStatus] = mapped_column(
        Enum(RideStatus, name="ride_status"), default=RideStatus.scheduled
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    driver: Mapped[User] = relationship(lazy="joined")
    bookings: Mapped[list["Booking"]] = relationship(back_populates="ride")


class Booking(Base):
    __tablename__ = "bookings"
    __table_args__ = (UniqueConstraint("ride_id", "rider_id", name="uq_booking_ride_rider"),)

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    ride_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("rides.id", ondelete="CASCADE"), index=True
    )
    rider_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    seats: Mapped[int] = mapped_column(Integer, default=1)
    status: Mapped[BookingStatus] = mapped_column(
        Enum(BookingStatus, name="booking_status"), default=BookingStatus.pending
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    ride: Mapped[Ride] = relationship(back_populates="bookings")
    rider: Mapped[User] = relationship(lazy="joined")


class Message(Base):
    __tablename__ = "messages"

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    ride_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("rides.id", ondelete="CASCADE"), index=True
    )
    sender_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE")
    )
    body: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), index=True
    )


class Rating(Base):
    __tablename__ = "ratings"
    __table_args__ = (UniqueConstraint("ride_id", "from_id", name="uq_rating_ride_from"),)

    id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True, default=uuid4)
    ride_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("rides.id", ondelete="CASCADE"), index=True
    )
    from_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), ForeignKey("users.id"))
    to_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), ForeignKey("users.id"), index=True)
    stars: Mapped[int] = mapped_column(Integer)
    text: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )


class FcmToken(Base):
    __tablename__ = "fcm_tokens"

    token: Mapped[str] = mapped_column(String(255), primary_key=True)
    user_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    platform: Mapped[str] = mapped_column(String(20), default="android")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )
