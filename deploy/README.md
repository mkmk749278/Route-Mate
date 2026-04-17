# VPS Deployment (MVP Baseline)

This directory contains the first-pass deployment assets for a single VPS rollout.

## Included assets

- `docker-compose.vps.yml` — production-oriented Compose stack (`postgres`, `api`, `nginx`)
- `nginx/route-mates.conf` — reverse proxy template for the API
- `.env.vps.example` — required environment variables for VPS deployment

Redis is intentionally not part of this MVP deployment stack because it is not used by the current backend runtime.

## First deployment steps

1. Copy and edit environment values:

   ```bash
   cp deploy/.env.vps.example deploy/.env.vps
   ```

2. Build and start services:

   ```bash
   docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml up -d --build
   ```

3. Verify health:

   ```bash
   curl http://<vps-ip>/health
   ```

`/health` currently validates database readiness (PostgreSQL) for this MVP deployment baseline.

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
