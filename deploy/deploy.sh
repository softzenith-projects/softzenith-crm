#!/usr/bin/env bash
# Updates the code, rebuilds the images and restarts what changed. Usage: deploy/deploy.sh [branch]
set -euo pipefail
cd "$(dirname "$0")/.."

branch="${1:-$(git rev-parse --abbrev-ref HEAD)}"
git fetch origin "$branch"
git checkout "$branch"
git pull --ff-only origin "$branch"

cd deploy
if [ ! -f .env ]; then
  echo "deploy/.env is missing: run deploy/setup-env.sh first." >&2
  exit 1
fi

# One image at a time: a 2 GB server runs out of memory building both together.
docker compose build api
docker compose build web
docker compose up -d --remove-orphans
docker image prune -f >/dev/null
docker compose ps
