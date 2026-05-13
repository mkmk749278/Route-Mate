"""user_settings table for quiet hours + per-kind notification mutes.

Revision ID: 0004
Revises: 0003
Create Date: 2026-05-13
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "user_settings",
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("quiet_start_hour", sa.Integer, nullable=True),
        sa.Column("quiet_end_hour", sa.Integer, nullable=True),
        sa.Column(
            "muted_kinds",
            postgresql.ARRAY(sa.Text),
            server_default=sa.text("'{}'::text[]"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            onupdate=sa.func.now(),
        ),
    )


def downgrade() -> None:
    op.drop_table("user_settings")
