#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
backup_dir="${BACKUP_DIR:-$root_dir/backups}"
mkdir -p "$backup_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$backup_dir/driperska-$timestamp.sql.gz"

cd "$root_dir"
docker compose exec -T db pg_dump -U driperska -d driperska --clean --if-exists   | gzip -9 > "$target"

# Keep 30 daily/deploy backups. Volumes are never removed by this script.
find "$backup_dir" -maxdepth 1 -type f -name 'driperska-*.sql.gz' -printf '%T@ %p\n'   | sort -nr | tail -n +31 | cut -d' ' -f2- | xargs -r rm --

echo "$target"