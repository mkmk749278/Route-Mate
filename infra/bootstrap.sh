#!/usr/bin/env bash
# One-shot VPS bootstrap. Run ONCE on a fresh Ubuntu VPS as root or via sudo.
# After this, every change ships via GitHub Actions (no SSH from a phone).
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/<owner>/Route-Mate/main/infra/bootstrap.sh | sudo bash -s -- \
#     --owner <github-owner> \
#     --domain api.example.com \
#     --postgres-pass <pw> \
#     --jwt-secret <pw> \
#     --ghcr-pat <token-with-read:packages> \
#     --firebase-admin-base64 <base64 of service account json>
set -euo pipefail

OWNER=""
DOMAIN=""
POSTGRES_PASSWORD=""
JWT_SECRET=""
GHCR_PAT=""
FIREBASE_ADMIN_B64=""
GHCR_USER="${GHCR_USER:-bootstrap}"
TARGET_DIR="${TARGET_DIR:-/opt/routemate}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --owner) OWNER="$2"; shift 2 ;;
    --domain) DOMAIN="$2"; shift 2 ;;
    --postgres-pass) POSTGRES_PASSWORD="$2"; shift 2 ;;
    --jwt-secret) JWT_SECRET="$2"; shift 2 ;;
    --ghcr-pat) GHCR_PAT="$2"; shift 2 ;;
    --firebase-admin-base64) FIREBASE_ADMIN_B64="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

for v in OWNER DOMAIN POSTGRES_PASSWORD JWT_SECRET GHCR_PAT FIREBASE_ADMIN_B64; do
  if [[ -z "${!v}" ]]; then echo "missing --${v,,/_/-}" >&2; exit 2; fi
done

# 1. Docker engine + compose plugin
if ! command -v docker >/dev/null 2>&1; then
  apt-get update
  apt-get install -y ca-certificates curl gnupg
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  source /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker

# 2. Source tree on disk (used only for compose file + Caddyfile).
mkdir -p "$TARGET_DIR"/{secrets}
curl -fsSL "https://raw.githubusercontent.com/${OWNER}/Route-Mate/main/infra/docker-compose.yml" \
  -o "$TARGET_DIR/docker-compose.yml"
curl -fsSL "https://raw.githubusercontent.com/${OWNER}/Route-Mate/main/infra/Caddyfile" \
  -o "$TARGET_DIR/Caddyfile"

# 3. Secrets
echo "$FIREBASE_ADMIN_B64" | base64 -d > "$TARGET_DIR/secrets/firebase-admin.json"
chmod 600 "$TARGET_DIR/secrets/firebase-admin.json"

cat > "$TARGET_DIR/.env" <<EOF
GHCR_OWNER=${OWNER}
IMAGE_TAG=latest
DOMAIN=${DOMAIN}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
JWT_SECRET=${JWT_SECRET}
EOF
chmod 600 "$TARGET_DIR/.env"

# 4. GHCR login (image pulls)
echo "$GHCR_PAT" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

# 5. First boot
cd "$TARGET_DIR"
docker compose pull || true
docker compose up -d
echo "Bootstrap complete. API will be at https://${DOMAIN}/v1/health once Caddy issues TLS."
