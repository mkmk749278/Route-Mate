#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]:-}"
SCRIPT_DIR="$(cd "$(dirname "${SCRIPT_PATH}")" >/dev/null 2>&1 && pwd || pwd)"

if [[ -d "${SCRIPT_DIR}/../.git" ]]; then
  DEFAULT_REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
else
  DEFAULT_REPO_DIR="${HOME}/Route-Mate"
fi

REPO_URL="https://github.com/mkmk749278/Route-Mate.git"
REPO_REF="main"
REPO_DIR="${DEFAULT_REPO_DIR}"
ENV_FILE=""
CORS_ORIGIN=""
HAS_CORS_ORIGIN_OVERRIDE="false"
AUTO_SECRETS="true"
NON_INTERACTIVE="true"
SKIP_DEPLOY="false"
TMP_DOCKER_INSTALL_DIR=""

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

cleanup() {
  if [[ -n "${TMP_DOCKER_INSTALL_DIR}" && -d "${TMP_DOCKER_INSTALL_DIR}" ]]; then
    rm -rf "${TMP_DOCKER_INSTALL_DIR}"
  fi
}

usage() {
  cat <<USAGE
Usage: ./deploy/bootstrap-vps.sh [options]

Options:
  --repo-url <url>        Git repository URL (default: ${REPO_URL})
  --repo-ref <ref>        Git branch/tag to deploy (default: ${REPO_REF})
  --repo-dir <path>       Local checkout path (default: ${DEFAULT_REPO_DIR})
  --env-file <path>       Deploy env file path passed to deploy-vps.sh
  --cors-origin <value>   Set CORS_ORIGIN (comma-separated http(s) origins)
  --non-interactive       Non-interactive deploy mode (default)
  --interactive           Allow prompts for missing values
  --auto-secrets          Auto-generate missing DB_PASSWORD/JWT_SECRET (default)
  --no-auto-secrets       Disable secret auto-generation
  --skip-deploy           Bootstrap host and repo only
  -h, --help              Show this help message
USAGE
}

to_abs_path() {
  local path="$1"
  if [[ "${path}" = /* ]]; then
    printf '%s\n' "${path}"
  else
    printf '%s\n' "$(pwd)/${path}"
  fi
}

run_privileged() {
  if [[ "${EUID}" -eq 0 ]]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    fail "This step requires root privileges. Install sudo or run as root."
  fi
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --repo-url)
        [[ $# -ge 2 ]] || fail "--repo-url requires a value"
        REPO_URL="$2"
        shift 2
        ;;
      --repo-ref)
        [[ $# -ge 2 ]] || fail "--repo-ref requires a value"
        REPO_REF="$2"
        shift 2
        ;;
      --repo-dir)
        [[ $# -ge 2 ]] || fail "--repo-dir requires a value"
        REPO_DIR="$(to_abs_path "$2")"
        shift 2
        ;;
      --env-file)
        [[ $# -ge 2 ]] || fail "--env-file requires a value"
        ENV_FILE="$(to_abs_path "$2")"
        shift 2
        ;;
      --cors-origin)
        [[ $# -ge 2 ]] || fail "--cors-origin requires a value"
        CORS_ORIGIN="$2"
        HAS_CORS_ORIGIN_OVERRIDE="true"
        shift 2
        ;;
      --non-interactive)
        NON_INTERACTIVE="true"
        shift
        ;;
      --interactive)
        NON_INTERACTIVE="false"
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
      --skip-deploy)
        SKIP_DEPLOY="true"
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
}

validate_platform() {
  [[ -f /etc/os-release ]] || fail "This script supports Ubuntu/Debian-like systems only."

  # shellcheck source=/dev/null
  source /etc/os-release

  if [[ "${ID:-}" != "ubuntu" && "${ID:-}" != "debian" && "${ID_LIKE:-}" != *"debian"* ]]; then
    fail "Unsupported distro: ${ID:-unknown}. Use this script on Ubuntu/Debian-like VPS only."
  fi

  command -v apt-get >/dev/null 2>&1 || fail "apt-get is required on Ubuntu/Debian VPS."
}

install_base_packages() {
  log "==> Installing base packages (git, curl, ca-certificates)"
  run_privileged apt-get update
  run_privileged apt-get install -y git curl ca-certificates
}

install_docker_if_needed() {
  if command -v docker >/dev/null 2>&1; then
    log "==> Docker already installed"
  else
    log "==> Installing Docker Engine"
    TMP_DOCKER_INSTALL_DIR="$(mktemp -d)"
    log "==> Downloading Docker's official convenience installer (https://get.docker.com)"
    curl -fsSL https://get.docker.com -o "${TMP_DOCKER_INSTALL_DIR}/get-docker.sh"
    run_privileged sh "${TMP_DOCKER_INSTALL_DIR}/get-docker.sh"
  fi

  if command -v systemctl >/dev/null 2>&1; then
    run_privileged systemctl enable --now docker || true
  fi

  if ! docker compose version >/dev/null 2>&1 && ! run_privileged docker compose version >/dev/null 2>&1; then
    log "==> Installing Docker Compose plugin"
    run_privileged apt-get install -y docker-compose-plugin
  fi

  if ! docker compose version >/dev/null 2>&1 && ! run_privileged docker compose version >/dev/null 2>&1; then
    fail "docker compose is still unavailable. Verify Docker installation and user permissions."
  fi
}

ensure_repo_checkout() {
  if [[ -d "${REPO_DIR}/.git" ]]; then
    log "==> Updating repository checkout at ${REPO_DIR}"

    if [[ -n "$(git -C "${REPO_DIR}" status --porcelain)" ]]; then
      fail "Repository at ${REPO_DIR} has uncommitted changes. Commit/stash them or choose a clean --repo-dir."
    fi

    local existing_origin=""
    existing_origin="$(git -C "${REPO_DIR}" remote get-url origin 2>/dev/null || true)"
    if [[ -n "${existing_origin}" && "${existing_origin}" != "${REPO_URL}" ]]; then
      fail "Repository at ${REPO_DIR} points to origin ${existing_origin}, expected ${REPO_URL}. Use --repo-dir or --repo-url to resolve."
    fi

    git -C "${REPO_DIR}" fetch --prune origin "${REPO_REF}"
    git -C "${REPO_DIR}" checkout -B "${REPO_REF}" FETCH_HEAD
    return 0
  fi

  if [[ -e "${REPO_DIR}" && -n "$(find "${REPO_DIR}" -mindepth 1 -maxdepth 1 2>/dev/null | head -n1)" ]]; then
    fail "${REPO_DIR} exists but is not a git checkout. Use an empty path or pass --repo-dir to another location."
  fi

  log "==> Cloning ${REPO_URL} (${REPO_REF}) into ${REPO_DIR}"
  git clone --branch "${REPO_REF}" "${REPO_URL}" "${REPO_DIR}"
}

run_deploy() {
  local deploy_script="${REPO_DIR}/deploy/deploy-vps.sh"
  [[ -x "${deploy_script}" ]] || fail "Missing deploy script: ${deploy_script}"

  local deploy_args=("${deploy_script}")

  if [[ "${NON_INTERACTIVE}" == "true" ]]; then
    deploy_args+=("--non-interactive")
  fi

  if [[ "${AUTO_SECRETS}" == "true" ]]; then
    deploy_args+=("--auto-secrets")
  else
    deploy_args+=("--no-auto-secrets")
  fi

  if [[ -n "${ENV_FILE}" ]]; then
    deploy_args+=("--env-file" "${ENV_FILE}")
  fi

  if [[ "${HAS_CORS_ORIGIN_OVERRIDE}" == "true" ]]; then
    if [[ -n "${CORS_ORIGIN}" ]]; then
      deploy_args+=("--cors-origin" "${CORS_ORIGIN}")
    else
      deploy_args+=("--cors-origin" "")
    fi
  fi

  log "==> Running deployment entrypoint"
  "${deploy_args[@]}"
}

show_assumptions() {
  log "==> Bootstrap configuration"
  log "    repo-url: ${REPO_URL}"
  log "    repo-ref: ${REPO_REF}"
  log "    repo-dir: ${REPO_DIR}"

  if [[ "${NON_INTERACTIVE}" == "true" ]]; then
    log "    mode: non-interactive"
  else
    log "    mode: interactive"
  fi

  if [[ "${AUTO_SECRETS}" == "true" ]]; then
    log "    secrets: auto-generate missing DB_PASSWORD/JWT_SECRET"
  else
    log "    secrets: manual only"
  fi

  if [[ -z "${CORS_ORIGIN//[[:space:]]/}" ]]; then
    log "    CORS_ORIGIN: not overridden (blank remains permissive allow-all in deploy flow)"
  else
    log "    CORS_ORIGIN: override provided"
  fi

  if [[ -n "${ENV_FILE}" ]]; then
    log "    env-file override: ${ENV_FILE}"
  fi
}

main() {
  trap cleanup EXIT
  parse_args "$@"
  show_assumptions
  validate_platform
  install_base_packages
  install_docker_if_needed
  ensure_repo_checkout

  if [[ "${SKIP_DEPLOY}" == "true" ]]; then
    log "✅ Bootstrap completed (deployment skipped by --skip-deploy)."
    return 0
  fi

  run_deploy
}

main "$@"
