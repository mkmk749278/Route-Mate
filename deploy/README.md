# VPS Deployment (MVP Baseline)

This directory contains the first-pass deployment assets for a single VPS rollout.

## Included assets

- `docker-compose.vps.yml` — production-oriented Compose stack (`postgres`, `api`, `nginx`)
- `nginx/route-mates.conf` — reverse proxy template for the API
- `.env.vps.example` — required environment variables for VPS deployment
- `bootstrap-vps.sh` — Ubuntu/Debian bootstrap entrypoint for near zero-touch VPS deployment

Redis is intentionally not part of this MVP deployment stack because it is not used by the current backend runtime.

## One-command deployment (primary path)

Fresh Ubuntu/Debian VPS bootstrap:

```bash
curl -fsSL https://raw.githubusercontent.com/mkmk749278/Route-Mate/main/deploy/bootstrap-vps.sh | bash
```

This path installs Docker + Compose, clones/updates the repo checkout, bootstraps `deploy/.env.vps`, generates strong secrets when needed, then runs deployment.

Default assumptions (printed by the script):

- repository URL: `https://github.com/mkmk749278/Route-Mate.git`
- checkout directory: `$HOME/Route-Mate`
- git ref: `main`
- non-interactive mode with secret auto-generation enabled
- no `CORS_ORIGIN` override (blank remains permissive)

Manual/advanced path from repository root:

```bash
./deploy/deploy-vps.sh
```

`deploy-vps.sh` automation behavior:

- creates env file from template when missing (`deploy/.env.vps` from `deploy/.env.vps.example`)
- validates required values and blocks placeholder secrets
- auto-generates strong URL-safe `DB_PASSWORD` and `JWT_SECRET` when missing/placeholder
- supports `--non-interactive`, `--env-file`, `--env-template`, `--compose-file`, `--no-auto-secrets`, `--cors-origin`
- runs `docker compose ... up -d --build --remove-orphans`, prints status, and performs localhost `/health` check

## One-command update (existing VPS checkout)

```bash
~/Route-Mate/deploy/bootstrap-vps.sh
```

Use this to safely update/redeploy; it refreshes the repo and re-runs deployment idempotently.

## Minimal VPS prerequisites

- Docker Engine installed and running
- Docker Compose plugin available (`docker compose`)
- `deploy/.env.vps` (auto-created when missing by `deploy-vps.sh`)

## Immediate verification

`/health` currently validates database readiness (PostgreSQL) for this MVP deployment baseline.

If needed, run manually:

```bash
curl -fsS http://<vps-ip>/health
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml ps
```

Optional override examples:

```bash
# deploy from a custom checkout location
curl -fsSL https://raw.githubusercontent.com/mkmk749278/Route-Mate/main/deploy/bootstrap-vps.sh | bash -s -- --repo-dir /opt/route-mate

# set CORS at deploy time without manual env editing
~/Route-Mate/deploy/bootstrap-vps.sh --cors-origin https://app.example.com
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
