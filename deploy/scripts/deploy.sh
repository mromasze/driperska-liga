#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root_dir"

if [[ ! -f .env ]]; then
  echo "Missing $root_dir/.env" >&2
  exit 1
fi

if docker compose --env-file .env ps --status running --services | grep -qx db; then
  echo "Creating pre-deploy database backup..."
  "$root_dir/deploy/scripts/backup.sh"
else
  echo "First deployment: database is not running yet, skipping pre-deploy backup."
fi

image_archive="$root_dir/release/docker-images.tar.gz"
if [[ ! -s "$image_archive" ]]; then
  echo "Missing prebuilt Docker package: $image_archive" >&2
  exit 1
fi

echo "Loading prebuilt application images..."
docker image load --input "$image_archive"
docker image inspect driperska-backend:latest driperska-web:latest >/dev/null

echo "Applying Flyway migrations and replacing application containers..."
docker compose --env-file .env up -d --remove-orphans --no-build

echo "Waiting for healthy containers..."
deadline=$((SECONDS + 180))
until web_id="$(docker compose --env-file .env ps -q web)"   && [[ -n "$web_id" ]]   && [[ "$(docker inspect --format='{{.State.Health.Status}}' "$web_id" 2>/dev/null || true)" == "healthy" ]]; do
  if (( SECONDS >= deadline )); then
    docker compose --env-file .env ps
    docker compose --env-file .env logs --tail=150 backend web
    exit 1
  fi
  sleep 3
done

docker compose --env-file .env ps
echo "Deployment complete."