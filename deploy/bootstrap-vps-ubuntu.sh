#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEPLOY_SCRIPT="${REPO_ROOT}/deploy/deploy-vps.sh"

AUTO_SECRETS="true"
NON_INTERACTIVE="true"
SKIP_DEPLOY="false"
TMP_DOCKER_INSTALL_DIR=""

log() {
  printf '%s\n' "$*"
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
Usage: ./deploy/bootstrap-vps-ubuntu.sh [options]

Options:
  --non-interactive     Pass --non-interactive to deploy script (default)
  --interactive         Allow interactive prompts during deploy
  --auto-secrets        Auto-generate missing DB_PASSWORD/JWT_SECRET (default)
  --no-auto-secrets     Require manual secret input/config
  --skip-deploy         Install prerequisites only; do not run deployment
  -h, --help            Show this help message
USAGE
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
    fail "Unsupported distro: ${ID:-unknown}. Use this script on Ubuntu (or Debian-like) VPS only."
  fi

  command -v apt-get >/dev/null 2>&1 || fail "apt-get is required on Ubuntu VPS."
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
    log "==> Review Docker's official installation documentation if your environment requires stricter change control."
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

run_deploy() {
  [[ -x "${DEPLOY_SCRIPT}" ]] || fail "Missing deploy script: ${DEPLOY_SCRIPT}"

  local deploy_args=("${DEPLOY_SCRIPT}")

  if [[ "${NON_INTERACTIVE}" == "true" ]]; then
    deploy_args+=("--non-interactive")
  fi

  if [[ "${AUTO_SECRETS}" == "true" ]]; then
    deploy_args+=("--auto-secrets")
  else
    deploy_args+=("--no-auto-secrets")
  fi

  log "==> Running deployment entrypoint"
  if docker compose version >/dev/null 2>&1; then
    "${deploy_args[@]}"
  else
    run_privileged "${deploy_args[@]}"
  fi
}

main() {
  trap cleanup EXIT
  parse_args "$@"
  validate_platform
  install_base_packages
  install_docker_if_needed

  if [[ "${SKIP_DEPLOY}" == "true" ]]; then
    log "✅ Bootstrap completed (deployment skipped by --skip-deploy)."
    return 0
  fi

  run_deploy
}

main "$@"
