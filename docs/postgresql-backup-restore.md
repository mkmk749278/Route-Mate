# PostgreSQL Backup/Restore (Single VPS MVP)

This is the baseline backup/restore process for Route Mates on one VPS.

## Backup

Run on VPS from repository root:

```bash
mkdir -p backups
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml exec -T postgres \
  pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "backups/route_mates_$(date +%F_%H%M%S).dump"
```

Recommended MVP baseline:
- daily backup
- keep at least 7 recent backups
- copy backups off-VPS (object storage or second machine)

## Restore (into clean target DB)

```bash
# drop/recreate target DB (use caution)
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml exec -T postgres \
  psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;"
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml exec -T postgres \
  psql -U "$DB_USER" -d postgres -c "CREATE DATABASE $DB_NAME;"

# restore selected dump file
cat backups/<backup-file>.dump | \
docker compose --env-file deploy/.env.vps -f deploy/docker-compose.vps.yml exec -T postgres \
  pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner --no-privileges
```

## Validation after restore

1. `curl http://<vps-ip>/health`
2. Run `docs/production-validation-smoke-test.md` quick path
3. Confirm expected routes/interests/users are present

## Secrets and safety

- Keep database credentials host-side only (`deploy/.env.vps` on VPS or equivalent secret injection).
- Do not commit backup files to the repository.
