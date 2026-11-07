#!/usr/bin/env bash
# Sets one value in deploy/.env without it appearing on screen or in the shell history:
#   deploy/env-set.sh MAIL_PASSWORD
# Then apply it: cd deploy && docker compose up -d api
set -euo pipefail
cd "$(dirname "$0")"

key="${1:-}"
if [[ ! "$key" =~ ^[A-Z0-9_]+$ ]]; then
  echo "Usage: deploy/env-set.sh NAME   (for example MAIL_PASSWORD)" >&2
  exit 2
fi
[ -f .env ] || { echo "deploy/.env is missing: run deploy/setup-env.sh first." >&2; exit 1; }

read -r -s -p "Value for $key (hidden): " value
echo
if [[ -z "$value" || "$value" == *'$'* ]]; then
  echo "Not changed: the value is empty or contains \$." >&2
  exit 1
fi

umask 077
tmp="$(mktemp .env.new.XXXXXX)"
trap 'rm -f "$tmp"' EXIT
found=0
while IFS= read -r line || [ -n "$line" ]; do
  if [[ "$line" == "$key="* ]]; then
    line="$key=$value"
    found=1
  fi
  printf '%s\n' "$line" >>"$tmp"
done <.env
[ "$found" = 1 ] || printf '%s=%s\n' "$key" "$value" >>"$tmp"
chmod 600 "$tmp"
mv "$tmp" .env
trap - EXIT
echo "$key saved in deploy/.env. Apply it with: cd $(pwd) && docker compose up -d api"
