#!/usr/bin/env bash
# Creates deploy/.env from staging.env.example with fresh random secrets. Never overwrites an existing .env.
# The secrets are made on the server, so they never travel through chat or Git.
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  echo "deploy/.env already exists; left unchanged."
  exit 0
fi

umask 077
cp "${1:-staging.env.example}" .env
for key in DB_PASSWORD CRM_AUTH_TOKEN_SECRET CRM_PLATFORM_JWT_SECRET MAILPIT_PASSWORD CRM_PLATFORM_ADMIN_PASSWORD; do
  sed -i "s|^${key}=\$|${key}=$(openssl rand -hex 24)|" .env
done
chmod 600 .env
echo "Created deploy/.env (readable only by $(whoami)). Save the passwords in your password manager:"
echo "  grep -E 'CRM_PLATFORM_ADMIN|MAILPIT_PASSWORD' $(pwd)/.env"
