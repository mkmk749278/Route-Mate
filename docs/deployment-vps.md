# Route Mates VPS Deployment (Phase 8)

This guide is the MVP deployment path for a single VPS.

## Prerequisites

- Docker Engine + Docker Compose plugin installed on the VPS
- Public DNS/domain pointed to VPS (recommended)
- Ports open: `80` (and `443` once TLS is configured)

## 1) Prepare deployment environment

From repository root:

```bash
cp deploy/.env.vps.example deploy/.env.vps
```

Set secure values in `deploy/.env.vps`:

- `DB_PASSWORD`
- `JWT_SECRET`
- `CORS_ORIGIN` (for example `https://api.example.com`, or comma-separated origins if needed)
  - leave blank only if you intentionally want permissive CORS (`allow-all`)

## 2) Start production stack

Primary command (from repository root):

```bash
./deploy/deploy-vps.sh
```

What this script does:
- validates Docker + Docker Compose availability
- validates `deploy/.env.vps` and `deploy/docker-compose.vps.yml`
- runs `docker compose ... up -d --build --remove-orphans`
- prints stack status and runs a retrying `curl http://localhost/health` check when available

Manual fallback:

```bash
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml up -d --build --remove-orphans
```

Services:

- `postgres` (persistent volume: `postgres_data`)
- `api` (NestJS backend)
- `nginx` (reverse proxy on port `80`)

## 3) Verify deployment

The deploy script already performs this check if `curl` is available. Manual check:

```bash
curl -fsS http://<vps-ip>/health
```

Expected response includes:

- `status: healthy`
- `checks.database: up`
- current readiness check verifies PostgreSQL connectivity (Redis is not part of this MVP stack)

Then run:

- production smoke test checklist: `docs/production-validation-smoke-test.md`
- operations runbook checks: `docs/operations-runbook.md`

## Migration execution path

The API startup command in `deploy/docker-compose.vps.yml` is:

```bash
npm run prisma:migrate:deploy && node dist/main
```

This ensures production-safe migration application on every deployment startup.

`apps/api/Dockerfile` includes Prisma schema/migrations in the runtime image so migration deploy can run in-container.

## Redis status

Redis is not included in this MVP deployment stack because current backend runtime does not use it.

## TLS/HTTPS

After HTTP verification, add TLS in your VPS setup (for example Certbot-managed certificates with Nginx). Keep certificate files and private keys outside this repository.

## Backup/restore baseline

Use `docs/postgresql-backup-restore.md` for single-VPS backup and restore commands.
