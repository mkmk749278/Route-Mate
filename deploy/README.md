# VPS Deployment (MVP Baseline)

This directory contains the first-pass deployment assets for a single VPS rollout.

## Included assets

- `docker-compose.vps.yml` — production-oriented Compose stack (`postgres`, `api`, `nginx`)
- `nginx/route-mates.conf` — reverse proxy template for the API
- `.env.vps.example` — required environment variables for VPS deployment

Redis is intentionally not part of this MVP deployment stack because it is not used by the current backend runtime.

## One-command deployment (primary path)

Fresh Ubuntu VPS bootstrap:

```bash
sudo apt-get update && sudo apt-get install -y git curl ca-certificates && curl -fsSL https://get.docker.com | sudo sh && sudo systemctl enable --now docker
git clone https://github.com/mkmk749278/Route-Mate.git
cd Route-Mate
```

Review the Docker install script before running it, or use the equivalent packages from Docker's official Ubuntu installation guide if you need a more controlled setup path.

From repository root:

1. Create and edit host-side env values:

```bash
cp deploy/.env.vps.example deploy/.env.vps
```

2. Run deployment:

```bash
./deploy/deploy-vps.sh
```

The script validates Docker + Docker Compose availability, checks required deployment files, runs Compose build/up, prints service status, and runs a localhost health check (with retries) when `curl` is installed.

## One-command update (existing VPS checkout)

```bash
git pull --ff-only && ./deploy/deploy-vps.sh
```

Use this after changing `deploy/.env.vps` or pulling newer application code on the VPS.

## Minimal VPS prerequisites

- Docker Engine installed and running
- Docker Compose plugin available (`docker compose`)
- `deploy/.env.vps` created with secure host-side values

## Immediate verification

`/health` currently validates database readiness (PostgreSQL) for this MVP deployment baseline.

If needed, run manually:

```bash
curl -fsS http://<vps-ip>/health
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml ps
```

## Manual compose command (fallback)

```bash
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml up -d --build --remove-orphans
```

## Useful commands

```bash
# stack status
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml ps

# API health from the VPS
curl -fsS http://localhost/health

# recent logs
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml logs api --tail=200
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml logs nginx --tail=200
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml logs postgres --tail=200
```

## Migration path

The API service starts with:

```bash
npm run prisma:migrate:deploy && node dist/main
```

This applies committed Prisma migrations before serving traffic.

Prisma schema and migration files are copied into the API runtime image via `apps/api/Dockerfile`.

If `CORS_ORIGIN` is left blank, API CORS behaves permissively (`allow-all`). Set a concrete origin (or comma-separated origins) in production.

## HTTPS/TLS (recommended next step)

Use your preferred TLS setup on the VPS after confirming HTTP deployment:

- Certbot + Nginx on host, or
- Cloudflare/edge TLS in front of VPS, or
- direct certificate mounts into Nginx container

Keep certificates and private keys outside the repository.

## Production validation + ops docs

- Smoke test checklist: `docs/production-validation-smoke-test.md`
- Operations runbook: `docs/operations-runbook.md`
- PostgreSQL backup/restore: `docs/postgresql-backup-restore.md`
