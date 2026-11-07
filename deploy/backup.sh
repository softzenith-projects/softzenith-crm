#!/usr/bin/env bash
# Dumps the database to deploy/backups/ (kept 7 days) and checks that the dump can be read back. When deploy/.env sets
# S3_BACKUP_BUCKET, also uploads it to S3 with the server's upload-only key (~/.aws, profile S3_BACKUP_PROFILE): that
# key cannot list, read or delete, and the bucket keeps each backup 35 days. Run nightly from cron (deploy/README.md).
set -euo pipefail
cd "$(dirname "$0")"

setting() { sed -n "s/^$1=//p" .env | tail -1; }

mkdir -p backups
chmod 700 backups
name="crm-$(date -u +%Y%m%d-%H%M).dump"
file="backups/$name"
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d crm -Fc' </dev/null >"$file.tmp"
# A truncated or empty dump fails here instead of replacing a good one.
docker compose exec -T db pg_restore --list <"$file.tmp" >/dev/null
mv "$file.tmp" "$file"
find backups -name 'crm-*.dump' -mtime +7 -delete
echo "$(date -u +%FT%TZ) backup ok: $file ($(du -h "$file" | cut -f1))"

bucket="$(setting S3_BACKUP_BUCKET)"
if [ -n "$bucket" ]; then
  profile="$(setting S3_BACKUP_PROFILE)"
  env="$(setting DEPLOY_ENV)"
  docker run --rm \
    -v "$HOME/.aws:/root/.aws:ro" \
    -v "$PWD/backups:/backups:ro" \
    -e AWS_PROFILE="${profile:-softcrm-backup}" \
    amazon/aws-cli:2.39.5 s3 cp "/backups/$name" "s3://$bucket/${env:-unknown}/$name" --only-show-errors
  echo "$(date -u +%FT%TZ) uploaded: s3://$bucket/${env:-unknown}/$name"
fi
