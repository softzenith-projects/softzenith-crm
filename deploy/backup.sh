#!/usr/bin/env bash
# Dumps the database to deploy/backups/ (kept 7 days) and checks that the dump can be read back. When deploy/.env sets
# S3_BACKUP_BUCKET, also uploads it to S3 with the server's upload-only key (~/.aws, profile S3_BACKUP_PROFILE): that
# key cannot list, read or delete, and the bucket keeps each backup 35 days. Run nightly from cron (deploy/README.md).
# When deploy/.env sets SENTRY_BACKUP_MONITOR (and SENTRY_API_DSN), each run checks in to that Sentry cron monitor:
# a failed or missing run, or a disk fuller than DISK_ALERT_PERCENT (default 85), emails an alert.
set -euo pipefail
cd "$(dirname "$0")"

setting() { sed -n "s/^$1=//p" .env | tail -1; }

cron_url=""
monitor="$(setting SENTRY_BACKUP_MONITOR)"
dsn="$(setting SENTRY_API_DSN)"
if [ -n "$monitor" ] && [[ "$dsn" =~ ^https://([^@]+)@([^/]+)/([0-9]+)$ ]]; then
  cron_url="https://${BASH_REMATCH[2]}/api/${BASH_REMATCH[3]}/cron/$monitor/${BASH_REMATCH[1]}/"
fi
checkin() { [ -z "$cron_url" ] || curl -fsS -m 10 -o /dev/null "$cron_url?status=$1" || true; }
trap 'checkin error' ERR
checkin in_progress

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

# Disk space: Oracle's metrics do not include it, so the nightly run checks it.
limit="$(setting DISK_ALERT_PERCENT)"
used="$(df --output=pcent / | tail -1 | tr -dc 0-9)"
if [ "$used" -ge "${limit:-85}" ]; then
  echo "$(date -u +%FT%TZ) DISK ALMOST FULL: / is ${used}% used (alert at ${limit:-85}%). Free space: docker image prune -a, old backups." >&2
  checkin error
  exit 1
fi
echo "$(date -u +%FT%TZ) disk ok: / is ${used}% used"
checkin ok
