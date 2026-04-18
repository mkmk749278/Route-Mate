#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

DEFAULT_ENV_FILE="${REPO_ROOT}/deploy/.env.vps"
DEFAULT_ENV_TEMPLATE="${REPO_ROOT}/deploy/.env.vps.example"
DEFAULT_COMPOSE_FILE="${REPO_ROOT}/deploy/docker-compose.vps.yml"

ENV_FILE="${DEFAULT_ENV_FILE}"
ENV_TEMPLATE=""
COMPOSE_FILE="${DEFAULT_COMPOSE_FILE}"
AUTO_SECRETS="true"
NON_INTERACTIVE="false"
RUN_HEALTH_CHECK="true"

log() {
  printf '%s\n' "$*"
}

warn() {
  printf 'WARNING: %s\n' "$*" >&2
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<USAGE
Usage: ./deploy/deploy-vps.sh [options]

Options:
  --env-file <path>       Use a custom env file (default: deploy/.env.vps)
  --compose-file <path>   Use a custom compose file (default: deploy/docker-compose.vps.yml)
  --env-template <path>   Template used when env file is missing (default: deploy/.env.vps.example)
  --non-interactive       Never prompt; fail with actionable errors when required input is missing
  --auto-secrets          Auto-generate missing/placeholder DB_PASSWORD and JWT_SECRET (default)
  --no-auto-secrets       Disable auto-generation of secrets
  --skip-health-check     Skip localhost health check
  -h, --help              Show this help message
USAGE
}

to_abs_path() {
  local path="$1"
  if [[ "${path}" = /* ]]; then
    printf '%s\n' "${path}"
  else
    printf '%s\n' "${REPO_ROOT}/${path}"
  fi
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --env-file)
        [[ $# -ge 2 ]] || fail "--env-file requires a value"
        ENV_FILE="$(to_abs_path "$2")"
        shift 2
        ;;
      --compose-file)
        [[ $# -ge 2 ]] || fail "--compose-file requires a value"
        COMPOSE_FILE="$(to_abs_path "$2")"
        shift 2
        ;;
      --env-template)
        [[ $# -ge 2 ]] || fail "--env-template requires a value"
        ENV_TEMPLATE="$(to_abs_path "$2")"
        shift 2
        ;;
      --non-interactive)
        NON_INTERACTIVE="true"
        shift
        ;;
      --auto-secrets)
        AUTO_SECRETS="true"
        shift
        ;;
      --no-auto-secrets)
        AUTO_SECRETS="false"
        shift
        ;;
      --skip-health-check)
        RUN_HEALTH_CHECK="false"
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "Unknown option: $1"
        ;;
    esac
  done

  if [[ -z "${ENV_TEMPLATE}" ]]; then
    ENV_TEMPLATE="${DEFAULT_ENV_TEMPLATE}"
  fi
}

check_command() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || fail "Required command not found: ${cmd}"
}

get_env_value() {
  local key="$1"
  local file="$2"

  while IFS='=' read -r current_key current_value || [[ -n "${current_key:-}" ]]; do
    [[ -n "${current_key:-}" ]] || continue
    [[ "${current_key}" =~ ^[[:space:]]*# ]] && continue

    if [[ "${current_key}" == "${key}" ]]; then
      printf '%s' "${current_value%$'\r'}"
      return 0
    fi
  done <"${file}"

  return 1
}

set_env_value() {
  local key="$1"
  local value="$2"
  local file="$3"
  local tmp_file

  tmp_file="$(mktemp)"
  awk -v k="${key}" -v v="${value}" '
    BEGIN { replaced=0 }
    index($0, k "=") == 1 { print k "=" v; replaced=1; next }
    { print }
    END { if (!replaced) print k "=" v }
  ' "${file}" >"${tmp_file}"

  mv "${tmp_file}" "${file}"
}

is_placeholder_or_empty() {
  local value="$1"
  local trimmed

  trimmed="${value//[[:space:]]/}"
  if [[ -z "${trimmed}" ]]; then
    return 0
  fi

  case "${value}" in
    replace_with_*|REPLACE_WITH_*|changeme|CHANGE_ME|your_*|YOUR_*)
      return 0
      ;;
  esac

  return 1
}

generate_urlsafe_secret() {
  local min_length="${1:-48}"
  local secret=""
  local bytes_needed

  if command -v openssl >/dev/null 2>&1; then
    # Base64 expands data by 4/3; +3 ensures rounded-up division by 4.
    bytes_needed=$(((min_length * 3 + 3) / 4))
    while [[ ${#secret} -lt ${min_length} ]]; do
      secret+="$(openssl rand -base64 "${bytes_needed}" | tr -d '\n=' | tr '/+' '_-')"
    done
    printf '%s' "${secret:0:${min_length}}"
    return 0
  fi

  if command -v python3 >/dev/null 2>&1; then
    python3 - "${min_length}" <<'PY'
import secrets
import sys

min_length = int(sys.argv[1])
value = ''
while len(value) < min_length:
    value += secrets.token_urlsafe(48)
print(value[:min_length], end='')
PY
    return 0
  fi

  fail "Cannot auto-generate secrets because neither openssl nor python3 is available. Install one of them or provide values manually in ${ENV_FILE}."
}

is_interactive() {
  [[ "${NON_INTERACTIVE}" == "false" && -t 0 && -t 1 ]]
}

validate_cors_origin_list() {
  local value="$1"
  local remaining
  local origin
  local trimmed

  remaining="${value}"
  while true; do
    origin="${remaining%%,*}"
    if [[ "${remaining}" == *","* ]]; then
      remaining="${remaining#*,}"
    else
      remaining=""
    fi

    trimmed="$(printf '%s' "${origin}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    if [[ -z "${trimmed}" ]]; then
      [[ -n "${remaining}" ]] || break
      continue
    fi

    if [[ ! "${trimmed}" =~ ^https?://[^[:space:]/?#]+(:[0-9]+)?$ ]]; then
      return 1
    fi

    [[ -n "${remaining}" ]] || break
  done

  return 0
}

bootstrap_env_file() {
  if [[ -f "${ENV_FILE}" ]]; then
    return 0
  fi

  [[ -f "${ENV_TEMPLATE}" ]] || fail "Missing ${ENV_FILE} and no template found at ${ENV_TEMPLATE}. Provide --env-template or create the env file manually."

  log "==> ${ENV_FILE} not found; creating it from ${ENV_TEMPLATE}"
  cp "${ENV_TEMPLATE}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}" || true
}

ensure_default_value() {
  local key="$1"
  local default_value="$2"
  local current_value=""

  current_value="$(get_env_value "${key}" "${ENV_FILE}" || true)"
  if is_placeholder_or_empty "${current_value}"; then
    set_env_value "${key}" "${default_value}" "${ENV_FILE}"
    log "==> Set ${key}=${default_value}"
  fi
}

prompt_secret_value() {
  local key="$1"
  local min_length="$2"
  local value=""

  while true; do
    read -r -s -p "Enter ${key} (min ${min_length} chars): " value
    printf '\n'

    if [[ ${#value} -lt ${min_length} ]]; then
      warn "${key} must be at least ${min_length} characters."
      continue
    fi

    printf '%s' "${value}"
    return 0
  done
}

ensure_secret_value() {
  local key="$1"
  local min_length="$2"
  local friendly_name="$3"
  local generated_length="$min_length"
  local current_value=""

  if [[ "${generated_length}" -lt 48 ]]; then
    generated_length=48
  fi

  current_value="$(get_env_value "${key}" "${ENV_FILE}" || true)"

  if is_placeholder_or_empty "${current_value}"; then
    if [[ "${AUTO_SECRETS}" == "true" ]]; then
      current_value="$(generate_urlsafe_secret "${generated_length}")"
      set_env_value "${key}" "${current_value}" "${ENV_FILE}"
      log "==> Generated ${friendly_name} and saved it to ${ENV_FILE}"
      return 0
    fi

    if is_interactive; then
      current_value="$(prompt_secret_value "${key}" "${min_length}")"
      set_env_value "${key}" "${current_value}" "${ENV_FILE}"
      log "==> Saved ${friendly_name} to ${ENV_FILE}"
      return 0
    fi

    fail "${key} is missing in ${ENV_FILE}. Re-run with --auto-secrets or set ${key} manually."
  fi

  if [[ ${#current_value} -lt ${min_length} ]]; then
    fail "${key} in ${ENV_FILE} must be at least ${min_length} characters."
  fi
}

handle_cors_origin() {
  local cors_origin=""
  local answer=""

  cors_origin="$(get_env_value "CORS_ORIGIN" "${ENV_FILE}" || true)"

  if [[ -n "${cors_origin//[[:space:]]/}" ]]; then
    validate_cors_origin_list "${cors_origin}" || fail "CORS_ORIGIN in ${ENV_FILE} must be a comma-separated list of http(s) origins."
  fi

  if [[ -z "${cors_origin//[[:space:]]/}" ]]; then
    warn "SECURITY WARNING: CORS_ORIGIN is empty. API will accept requests from any origin."

    if is_interactive; then
      read -r -p "Continue with permissive CORS for now? [y/N]: " answer
      if [[ ! "${answer}" =~ ^[Yy]$ ]]; then
        read -r -p "Enter CORS_ORIGIN (example: https://app.example.com): " cors_origin
        [[ -n "${cors_origin//[[:space:]]/}" ]] || fail "CORS_ORIGIN cannot be blank after declining permissive CORS."
        validate_cors_origin_list "${cors_origin}" || fail "CORS_ORIGIN must use http:// or https:// values, comma-separated if multiple."
        set_env_value "CORS_ORIGIN" "${cors_origin}" "${ENV_FILE}"
        log "==> Saved CORS_ORIGIN to ${ENV_FILE}"
      fi
    fi
  fi
}

prepare_environment() {
  bootstrap_env_file

  ensure_default_value "APP_ENV" "production"
  ensure_default_value "API_PORT" "3000"
  ensure_default_value "JWT_EXPIRES_IN" "7d"
  ensure_default_value "DB_NAME" "route_mates"
  ensure_default_value "DB_USER" "route_mates"

  ensure_secret_value "DB_PASSWORD" 16 "DB password"
  ensure_secret_value "JWT_SECRET" 16 "JWT secret"
  handle_cors_origin
}

check_prerequisites() {
  check_command docker

  if ! docker compose version >/dev/null 2>&1; then
    fail "Docker Compose plugin is required (docker compose)."
  fi

  [[ -f "${ENV_FILE}" ]] || fail "Missing ${ENV_FILE}."
  [[ -f "${COMPOSE_FILE}" ]] || fail "Missing ${COMPOSE_FILE}."
}

deploy_stack() {
  local compose_args=(--env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

  log "==> Building and starting VPS stack"
  docker compose "${compose_args[@]}" up -d --build --remove-orphans
}

show_status() {
  local compose_args=(--env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

  log "==> Service status"
  docker compose "${compose_args[@]}" ps
}

health_check() {
  if [[ "${RUN_HEALTH_CHECK}" != "true" ]]; then
    log "==> Skipping health check (--skip-health-check)"
    return 0
  fi

  if command -v curl >/dev/null 2>&1; then
    log "==> Health check (http://localhost/health)"
    local max_attempts=30
    local attempt=1

    until curl -fsS http://localhost/health; do
      if [[ "${attempt}" -ge "${max_attempts}" ]]; then
        fail "Health check failed after ${max_attempts} attempts. Check: docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE} logs --tail=200"
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
  parse_args "$@"

  log "==> Using env file: ${ENV_FILE}"
  log "==> Using compose file: ${COMPOSE_FILE}"

  prepare_environment
  check_prerequisites
  deploy_stack
  show_status
  health_check

  log "✅ Deployment command completed."
  log "Next: run docs/production-validation-smoke-test.md"
}

main "$@"
