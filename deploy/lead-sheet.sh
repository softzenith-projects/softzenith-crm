#!/usr/bin/env bash
# Connects a business to its Google Sheet: stores the sheet script's web-app URL and a new random secret in the
# business's settings, then prints the secret to paste into the script (Project Settings → Script properties → SECRET).
# Usage:  deploy/lead-sheet.sh <business-slug> <web-app-url>   connect (or reconnect with a new secret)
#         deploy/lead-sheet.sh <business-slug> off             stop copying enquiries to the sheet
set -euo pipefail
cd "$(dirname "$0")"

usage="usage: deploy/lead-sheet.sh <business-slug> <web-app-url|off>"
slug="${1:?$usage}"
url="${2:?$usage}"
secret=""

if [ "$url" = "off" ]; then
  sql="update tenants set settings = settings - 'leadSheet' where slug = :'slug' returning slug;"
else
  case "$url" in
    https://script.google.com/*) ;;
    *) echo "The URL must be the Apps Script web-app URL (https://script.google.com/macros/s/.../exec)." >&2; exit 1 ;;
  esac
  secret="$(openssl rand -hex 24)"
  sql="update tenants set settings = jsonb_set(settings, '{leadSheet}', jsonb_build_object('url', :'url', 'secret', :'secret')) where slug = :'slug' returning slug;"
fi

updated="$(echo "$sql" | docker compose exec -T -e SLUG="$slug" -e URL="$url" -e SECRET="$secret" db \
  sh -c 'psql -U "$POSTGRES_USER" -d crm -v ON_ERROR_STOP=1 -q -t -A -v slug="$SLUG" -v url="$URL" -v secret="$SECRET"')"

if [ "$updated" != "$slug" ]; then
  echo "No business with the slug '$slug'; nothing changed." >&2
  exit 1
fi
if [ "$url" = "off" ]; then
  echo "Lead sheet switched off for '$slug'."
else
  echo "Lead sheet connected for '$slug'. In the sheet script: Project Settings → Script properties → add"
  echo "  Property: SECRET"
  echo "  Value:    $secret"
fi
