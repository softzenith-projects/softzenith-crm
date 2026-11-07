#!/usr/bin/env bash
# Creates deploy/.env for this server: deploy/setup-env.sh staging|prod. Never overwrites an existing .env.
#   - every empty value under "# GENERATED" in <env>.env.example gets a fresh random secret, made here on the server,
#     so secrets never travel through chat or Git;
#   - every empty value under "# ASK ..." / "# ASK-SECRET ..." is taken from an environment variable of the same name
#     if set, otherwise asked for (typing hidden for ASK-SECRET); without a terminal it stays empty, to be filled in
#     later with deploy/env-set.sh.
set -euo pipefail
cd "$(dirname "$0")"

env="${1:-}"
case "$env" in
  staging | prod) ;;
  *) echo "Usage: deploy/setup-env.sh staging|prod" >&2; exit 2 ;;
esac

if [ -f .env ]; then
  echo "deploy/.env already exists; left unchanged."
  exit 0
fi

umask 077
tmp="$(mktemp .env.new.XXXXXX)"
trap 'rm -f "$tmp"' EXIT

pending="" question="" empty=()
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    "# GENERATED"*) pending=generated ;;
    "# ASK-SECRET "*) pending=secret question="${line#\# ASK-SECRET }" ;;
    "# ASK "*) pending=ask question="${line#\# ASK }" ;;
    "#"* | "") ;;
    *)
      if [[ -n "$pending" && "$line" =~ ^([A-Z0-9_]+)=$ ]]; then
        key="${BASH_REMATCH[1]}"
        value=""
        if [ "$pending" = generated ]; then
          value="$(openssl rand -hex 24)"
        elif [ -n "${!key:-}" ]; then
          value="${!key}"
        elif [ -t 0 ]; then
          if [ "$pending" = secret ]; then
            read -r -s -p "$question ($key, hidden): " value
            echo
          else
            read -r -p "$question ($key): " value
          fi
        fi
        if [[ "$value" == *'$'* ]]; then
          echo "$key: a value containing \$ is not supported in deploy/.env" >&2
          exit 1
        fi
        [ -n "$value" ] || empty+=("$key")
        line="$key=$value"
      fi
      pending=""
      ;;
  esac
  printf '%s\n' "$line" >>"$tmp"
done <"$env.env.example"

chmod 600 "$tmp"
mv "$tmp" .env
trap - EXIT
echo "Created deploy/.env for $env (readable only by $(whoami))."
if [ "${#empty[@]}" -gt 0 ]; then
  echo "Still empty: ${empty[*]}. Fill in each with: deploy/env-set.sh <NAME>"
fi
echo "Save the console password in your password manager: grep CRM_PLATFORM_ADMIN_PASSWORD $(pwd)/.env"
