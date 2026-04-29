"""add rides.recurrence_days

Revision ID: 0003
Revises: 0002
Create Date: 2026-04-29
"""
import sqlalchemy as sa
from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "rides",
        sa.Column(
            "recurrence_days",
            sa.Integer,
            nullable=False,
            server_default="0",
        ),
    )


def downgrade() -> None:
    op.drop_column("rides", "recurrence_days")
