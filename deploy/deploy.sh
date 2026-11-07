#!/usr/bin/env bash
# Updates the code, rebuilds the images and restarts what changed. Usage: deploy/deploy.sh [branch]
# Prod (DEPLOY_ENV=prod in deploy/.env) runs only `main`; ALLOW_NON_MAIN=1 overrides that for an emergency.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "deploy/.env is missing: run deploy/setup-env.sh staging|prod first." >&2
  exit 1
fi

# deploy/.env made before DEPLOY_ENV existed (the first staging server): it uses the test inbox, so it is staging.
if ! grep -q '^DEPLOY_ENV=' .env; then
  if grep -q '^MAIL_HOST=mailpit$' .env; then
    printf '\n# Added by deploy.sh: this server is staging (Mailpit and the test inbox run on staging only)\n%s\n%s\n' \
      'DEPLOY_ENV=staging' 'COMPOSE_PROFILES=staging' >>.env
    echo "deploy/.env: added DEPLOY_ENV=staging and COMPOSE_PROFILES=staging"
  else
    echo "deploy/.env has no DEPLOY_ENV: add DEPLOY_ENV=staging or DEPLOY_ENV=prod." >&2
    exit 1
  fi
fi
deploy_env="$(sed -n 's/^DEPLOY_ENV=//p' .env | tail -1)"

cd ..
branch="${1:-$(git rev-parse --abbrev-ref HEAD)}"
if [ "$deploy_env" = prod ] && [ "$branch" != main ] && [ "${ALLOW_NON_MAIN:-}" != 1 ]; then
  echo "This is the prod server: it runs only main (asked for: $branch)." >&2
  exit 1
fi
git fetch origin "$branch"
git checkout "$branch"
git pull --ff-only origin "$branch"

cd deploy
# One image at a time: a small server runs out of memory building both together.
docker compose build api
docker compose build web
docker compose up -d --remove-orphans
docker image prune -f >/dev/null
docker compose ps
