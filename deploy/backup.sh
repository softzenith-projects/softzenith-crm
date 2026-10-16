#!/usr/bin/env bash
# Dumps the database to deploy/backups/ and keeps the last 7 days. Run nightly from cron (see deploy/README.md).
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p backups
chmod 700 backups
file="backups/crm-$(date -u +%Y%m%d-%H%M).dump"
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d crm -Fc' > "$file.tmp"
mv "$file.tmp" "$file"
find backups -name 'crm-*.dump' -mtime +7 -delete
echo "$(date -u +%FT%TZ) backup ok: $file ($(du -h "$file" | cut -f1))"
