"""saved_routes table for the Phase 10 driver-shortcut feature.

Revision ID: 0009
Revises: 0008
Create Date: 2026-05-13
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0009"
down_revision = "0008"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "saved_routes",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("name", sa.String(120), nullable=False),
        sa.Column("origin_lat", sa.Numeric(9, 6), nullable=False),
        sa.Column("origin_lng", sa.Numeric(9, 6), nullable=False),
        sa.Column("destination_lat", sa.Numeric(9, 6), nullable=False),
        sa.Column("destination_lng", sa.Numeric(9, 6), nullable=False),
        sa.Column("origin_label", sa.String(200), nullable=False),
        sa.Column("destination_label", sa.String(200), nullable=False),
        sa.Column(
            "recurrence_days", sa.Integer, server_default="0", nullable=False
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
        ),
        sa.UniqueConstraint("user_id", "name", name="uq_saved_route_user_name"),
    )


def downgrade() -> None:
    op.drop_table("saved_routes")
