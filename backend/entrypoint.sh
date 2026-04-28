#!/usr/bin/env bash
set -euo pipefail

# Run migrations on startup; safe to re-run.
alembic upgrade head

exec "$@"
