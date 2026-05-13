"""users.trusted_contacts + users.blocked_user_ids for Phase 2 (Safety v1).

Revision ID: 0005
Revises: 0004
Create Date: 2026-05-13
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column(
            "trusted_contacts",
            postgresql.JSONB,
            server_default=sa.text("'[]'::jsonb"),
            nullable=False,
        ),
    )
    op.add_column(
        "users",
        sa.Column(
            "blocked_user_ids",
            postgresql.ARRAY(postgresql.UUID(as_uuid=True)),
            server_default=sa.text("'{}'::uuid[]"),
            nullable=False,
        ),
    )


def downgrade() -> None:
    op.drop_column("users", "blocked_user_ids")
    op.drop_column("users", "trusted_contacts")
