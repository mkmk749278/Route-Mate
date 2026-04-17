# Route Mates MVP Operations Runbook (Single VPS)

This runbook is for first live rollout verification and early operational response.

## 1) First deployment verification

1. Deploy or update the stack:

   ```bash
   ./deploy/deploy-vps.sh
   ```

   The deploy script prints `docker compose ... ps` and runs a localhost health check.

2. Confirm API readiness from your workstation or VPS:

   ```bash
   curl -sS http://<vps-ip>/health
   ```

3. Run the production smoke test checklist:
   - `docs/production-validation-smoke-test.md`

## 2) Log inspection basics

```bash
# API logs
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml logs api --tail=200

# Nginx logs
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml logs nginx --tail=200

# Postgres logs
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml logs postgres --tail=200
```

For live follow mode, add `-f`.

## 3) Rollback basics (MVP-safe)

If a deployment fails validation:

1. Check out the last known-good commit/tag on the VPS working copy.
2. Rebuild and restart with that revision:

   ```bash
   ./deploy/deploy-vps.sh
   ```

3. Re-run `/health` and key smoke steps.

Notes:
- Keep secrets in host-side env files/systemd/env injection (never commit secrets).
- If a migration has already been applied, rollback should prioritize restoring service quickly and use DB restore only if needed.
