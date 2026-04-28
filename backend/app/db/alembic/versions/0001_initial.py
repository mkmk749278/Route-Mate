"""initial schema

Revision ID: 0001
Revises:
Create Date: 2026-04-28
"""
import sqlalchemy as sa
from alembic import op
from geoalchemy2 import Geography
from sqlalchemy.dialects import postgresql

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS postgis")

    op.create_table(
        "users",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("firebase_uid", sa.String(128), nullable=False, unique=True),
        sa.Column("phone", sa.String(32)),
        sa.Column("name", sa.String(120)),
        sa.Column("photo_url", sa.String(500)),
        sa.Column("rating_avg", sa.Numeric(3, 2), server_default="0"),
        sa.Column("rating_count", sa.Integer, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_users_phone", "users", ["phone"])

    ride_status = postgresql.ENUM(
        "scheduled", "started", "completed", "cancelled",
        name="ride_status", create_type=False,
    )
    ride_status.create(op.get_bind(), checkfirst=True)

    op.create_table(
        "rides",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "driver_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("origin", Geography("POINT", srid=4326), nullable=False),
        sa.Column("destination", Geography("POINT", srid=4326), nullable=False),
        sa.Column("origin_label", sa.String(200), nullable=False),
        sa.Column("destination_label", sa.String(200), nullable=False),
        sa.Column("polyline", sa.Text),
        sa.Column("depart_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("seats_total", sa.Integer, nullable=False),
        sa.Column("price_per_seat", sa.Numeric(8, 2), nullable=False),
        sa.Column("status", ride_status, server_default="scheduled"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_rides_driver_id", "rides", ["driver_id"])
    op.create_index("ix_rides_depart_at", "rides", ["depart_at"])
    op.execute("CREATE INDEX ix_rides_origin_gix ON rides USING GIST (origin)")
    op.execute("CREATE INDEX ix_rides_destination_gix ON rides USING GIST (destination)")

    booking_status = postgresql.ENUM(
        "pending", "accepted", "rejected", "cancelled",
        name="booking_status", create_type=False,
    )
    booking_status.create(op.get_bind(), checkfirst=True)

    op.create_table(
        "bookings",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "ride_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("rides.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "rider_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("seats", sa.Integer, server_default="1"),
        sa.Column("status", booking_status, server_default="pending"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.UniqueConstraint("ride_id", "rider_id", name="uq_booking_ride_rider"),
    )
    op.create_index("ix_bookings_ride_id", "bookings", ["ride_id"])
    op.create_index("ix_bookings_rider_id", "bookings", ["rider_id"])

    op.create_table(
        "messages",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "ride_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("rides.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "sender_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("body", sa.Text, nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_messages_ride_id", "messages", ["ride_id"])
    op.create_index("ix_messages_created_at", "messages", ["created_at"])

    op.create_table(
        "ratings",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "ride_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("rides.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "from_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id"), nullable=False
        ),
        sa.Column(
            "to_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id"), nullable=False
        ),
        sa.Column("stars", sa.Integer, nullable=False),
        sa.Column("text", sa.Text),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.UniqueConstraint("ride_id", "from_id", name="uq_rating_ride_from"),
    )
    op.create_index("ix_ratings_ride_id", "ratings", ["ride_id"])
    op.create_index("ix_ratings_to_id", "ratings", ["to_id"])

    op.create_table(
        "fcm_tokens",
        sa.Column("token", sa.String(255), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("platform", sa.String(20), server_default="android"),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_fcm_tokens_user_id", "fcm_tokens", ["user_id"])


def downgrade() -> None:
    op.drop_table("fcm_tokens")
    op.drop_table("ratings")
    op.drop_table("messages")
    op.drop_table("bookings")
    op.drop_table("rides")
    op.drop_table("users")
    sa.Enum(name="booking_status").drop(op.get_bind(), checkfirst=True)
    sa.Enum(name="ride_status").drop(op.get_bind(), checkfirst=True)
