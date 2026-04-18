#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NEW_SCRIPT="${SCRIPT_DIR}/bootstrap-vps.sh"

if [[ ! -x "${NEW_SCRIPT}" ]]; then
  echo "ERROR: Missing ${NEW_SCRIPT}" >&2
  exit 1
fi

echo "WARNING: deploy/bootstrap-vps-ubuntu.sh is deprecated. Use deploy/bootstrap-vps.sh instead."
exec "${NEW_SCRIPT}" "$@"
