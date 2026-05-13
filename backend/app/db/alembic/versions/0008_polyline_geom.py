"""Polyline-aware ride search.

Adds Geography(LINESTRING) `polyline_geom` to rides + a GIST index. The
existing ride.polyline TEXT column (an encoded polyline for the client
map) stays as-is; this new geometry is what Postgres uses for spatial
joins in /v1/rides/search.

Revision ID: 0008
Revises: 0007
Create Date: 2026-05-13
"""
from alembic import op
from geoalchemy2 import Geography
from sqlalchemy import Column

revision = "0008"
down_revision = "0007"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "rides",
        Column("polyline_geom", Geography("LINESTRING", srid=4326), nullable=True),
    )
    op.execute(
        "CREATE INDEX IF NOT EXISTS ix_rides_polyline_geom_gix "
        "ON rides USING GIST (polyline_geom)"
    )


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS ix_rides_polyline_geom_gix")
    op.drop_column("rides", "polyline_geom")
