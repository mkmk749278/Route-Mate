#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${REPO_ROOT}/deploy/.env.vps"
COMPOSE_FILE="${REPO_ROOT}/deploy/docker-compose.vps.yml"
COMPOSE_ARGS=(--env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

log() {
  printf '%s\n' "$*"
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

check_command() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || fail "Required command not found: ${cmd}"
}

check_prerequisites() {
  check_command docker

  if ! docker compose version >/dev/null 2>&1; then
    fail "Docker Compose plugin is required (docker compose)."
  fi

  [[ -f "${ENV_FILE}" ]] || fail "Missing ${ENV_FILE}. Create it from deploy/.env.vps.example."
  [[ -f "${COMPOSE_FILE}" ]] || fail "Missing ${COMPOSE_FILE}."
}

deploy_stack() {
  log "==> Building and starting VPS stack"
  docker compose "${COMPOSE_ARGS[@]}" up -d --build --remove-orphans
}

show_status() {
  log "==> Service status"
  docker compose "${COMPOSE_ARGS[@]}" ps
}

health_check() {
  if command -v curl >/dev/null 2>&1; then
    log "==> Health check (http://localhost/health)"
    local max_attempts=30
    local attempt=1

    until curl -fsS http://localhost/health; do
      if [[ "${attempt}" -ge "${max_attempts}" ]]; then
        fail "Health check failed after ${max_attempts} attempts. Check docker compose logs."
      fi

      attempt=$((attempt + 1))
      sleep 2
    done

    printf '\n'
  else
    log "==> curl not found; skip automatic health check."
    log "Run this manually: curl -fsS http://localhost/health"
  fi
}

main() {
  check_prerequisites
  deploy_stack
  show_status
  health_check
  log "✅ Deployment command completed."
  log "Next: run docs/production-validation-smoke-test.md"
}

main "$@"
