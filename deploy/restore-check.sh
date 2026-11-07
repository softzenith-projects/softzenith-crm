#!/usr/bin/env bash
# Proves a backup can be restored: loads it into a scratch database next to the real one, prints what it holds,
# then deletes the scratch database. The real database is not touched.
#   deploy/restore-check.sh                          newest dump in deploy/backups/
#   deploy/restore-check.sh backups/crm-....dump     a given dump (e.g. one downloaded from S3)
set -euo pipefail
cd "$(dirname "$0")"

dump="${1:-$(ls -1t backups/crm-*.dump 2>/dev/null | head -1)}"
[ -n "$dump" ] && [ -f "$dump" ] || { echo "No dump found (run deploy/backup.sh first)." >&2; exit 1; }

scratch=crm_restore_check
su="$(docker compose exec -T db sh -c 'printf %s "$POSTGRES_USER"')"

docker compose exec -T db sh -c "dropdb -U \"\$POSTGRES_USER\" --if-exists $scratch && createdb -U \"\$POSTGRES_USER\" $scratch"
trap 'docker compose exec -T db dropdb -U "$su" --if-exists '"$scratch"' >/dev/null 2>&1 || true' EXIT
docker compose exec -T db pg_restore -U "$su" -d "$scratch" --no-owner --no-privileges --exit-on-error <"$dump"

echo "Restored $dump into a scratch database:"
docker compose exec -T db psql -U "$su" -d "$scratch" -v ON_ERROR_STOP=1 -At -F ': ' \
  -c "select 'migrations applied', max(version::int) from flyway_schema_history where success" \
  -c "select 'tables', count(*) from information_schema.tables where table_schema = 'public'" \
  -c "select 'businesses', count(*) from tenants" \
  -c "select 'staff', count(*) from app_users" \
  -c "select 'leads', count(*) from leads"
echo "Restore check ok (scratch database deleted)."
