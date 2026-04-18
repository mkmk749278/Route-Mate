# Route Mates VPS Deployment (Phase 8)

This guide is the MVP deployment path for a single VPS.

## Prerequisites

- Public DNS/domain pointed to VPS (recommended)
- Ports open: `80` (and `443` once TLS is configured)

## 1) Near one-click Ubuntu deploy (recommended)

From a fresh Ubuntu/Debian VPS (single command):

```bash
curl -fsSL https://raw.githubusercontent.com/mkmk749278/Route-Mate/main/deploy/bootstrap-vps.sh | bash
```

What this automates:

- installs `git`, `curl`, `ca-certificates`, Docker Engine, Docker Compose plugin
- clones repository when missing, or updates existing checkout safely
- creates `deploy/.env.vps` from `deploy/.env.vps.example` when missing
- auto-generates strong values for missing/placeholder `DB_PASSWORD` and `JWT_SECRET`
- starts the production stack and runs localhost `/health` checks

Optional flags:

- `--repo-dir <path>`
- `--repo-ref <branch-or-tag>`
- `--non-interactive` / `--interactive`
- `--no-auto-secrets`
- `--cors-origin <origin[,origin2]>`

If `CORS_ORIGIN` is blank, API CORS is intentionally permissive (`allow-all`) and the deploy output calls this out explicitly.

Update/redeploy on an existing VPS checkout:

```bash
~/Route-Mate/deploy/bootstrap-vps.sh
```

## 2) Manual/advanced deployment command

From repository root:

```bash
./deploy/deploy-vps.sh
```

What this script does:
- validates Docker + Docker Compose availability
- bootstraps `deploy/.env.vps` from template when missing
- validates required env values and blocks placeholder secrets
- can auto-generate strong secrets (`DB_PASSWORD`, `JWT_SECRET`)
- runs `docker compose ... up -d --build --remove-orphans`
- prints stack status and runs a retrying `curl http://localhost/health` check when available

Useful flags:

- `--non-interactive` (no prompts; fail fast with actionable errors)
- `--env-file <path>`
- `--compose-file <path>`
- `--no-auto-secrets` (require manual secret values)
- `--cors-origin <origin[,origin2]>`

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
