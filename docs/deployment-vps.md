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

## 2) Start production stack

```bash
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml up -d --build
```

Services:

- `postgres` (persistent volume: `postgres_data`)
- `api` (NestJS backend)
- `nginx` (reverse proxy on port `80`)

## 3) Verify deployment

```bash
curl http://<vps-ip>/health
```

Expected response includes:

- `status: healthy`
- `checks.database: up`

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
