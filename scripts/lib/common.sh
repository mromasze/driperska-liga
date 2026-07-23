#!/usr/bin/env bash
#
# common.sh — shared helpers for the Driperska Liga test scripts. Source it:
#   source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
#
# Config (env vars, all optional):
#   BASE_URL     Backend base URL   (default: http://localhost:8080)
#   ADMIN_USER   Admin login        (default: admin)
#   ADMIN_PASS   Admin password     (default: changeit123)
#   BOTS_FILE    Bot credentials    (default: scripts/test-bots.json)
#
set -euo pipefail

ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-changeit123}"

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="$(dirname "$LIB_DIR")"
BOTS_FILE="${BOTS_FILE:-$SCRIPTS_DIR/test-bots.json}"
HUMAN_FILE="${HUMAN_FILE:-$SCRIPTS_DIR/.human.txt}"

command -v jq   >/dev/null || { echo "jq is required (apt install jq)" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required"               >&2; exit 1; }

# Resolve the backend URL for local testing. We probe the public /api/v1/config endpoint
# (unauthenticated) on the common local targets and use the first that answers. Set BASE_URL
# explicitly to skip auto-detection. Covered: standalone backend / docker web (8080) and the
# dev-compose backend port (8081), on both localhost and 127.0.0.1.
_probe() { curl -sS -o /dev/null -m 2 -w '%{http_code}' "$1/api/v1/config" 2>/dev/null || echo 000; }

_resolve_base_url() {
  local candidates=()
  [[ -n "${BASE_URL:-}" ]] && candidates+=("$BASE_URL")
  candidates+=(http://localhost:8080 http://127.0.0.1:8080 http://localhost:8081 http://127.0.0.1:8081)
  local c code seen=""
  for c in "${candidates[@]}"; do
    case " $seen " in *" $c "*) continue;; esac
    seen="$seen $c"
    code="$(_probe "$c")"
    if [[ "$code" =~ ^(200|401|403)$ ]]; then
      BASE_URL="$c"
      [[ -n "${BASE_URL_QUIET:-}" ]] || echo "→ Backend: $BASE_URL" >&2
      return 0
    fi
  done
  echo "✗ Nie znaleziono działającego backendu (próbowano:${seen})." >&2
  echo "  Uruchom lokalnie, np.:  docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build" >&2
  echo "  albo wskaż adres ręcznie:  BASE_URL=http://localhost:PORT $0" >&2
  exit 1
}
_resolve_base_url

# REQ METHOD PATH [BODY] [BEARER] -> sets globals RESP (body) and HTTP_CODE.
RESP=""; HTTP_CODE=""
REQ() {
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-sS -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body"  ]] && args+=(-d "$body")
  # curl appends the 3-digit status at the very end of stdout; slice it off.
  local out; out="$(curl "${args[@]}" -w '%{http_code}' 2>/dev/null || true)"
  HTTP_CODE="${out: -3}"
  RESP="${out:0:${#out}-3}"
}

# login USER PASS -> echoes access token (empty on failure).
login() {
  local user="$1" pass="$2"
  REQ POST /api/v1/auth/login "$(jq -n --arg u "$user" --arg p "$pass" '{username:$u,password:$p}')"
  [[ "$HTTP_CODE" == "200" ]] || return 1
  jq -r '.accessToken' <<<"$RESP"
}

# admin_login -> sets global ADMIN_TOKEN.
ADMIN_TOKEN=""
admin_login() {
  ADMIN_TOKEN="$(login "$ADMIN_USER" "$ADMIN_PASS")" \
    || { echo "✗ Logowanie admina nie powiodło się na $BASE_URL (HTTP $HTTP_CODE)" >&2; exit 1; }
}

require_bots_file() {
  [[ -f "$BOTS_FILE" ]] || {
    echo "✗ Nie znaleziono $BOTS_FILE — uruchom najpierw: ./scripts/seed-test-players.sh" >&2; exit 1;
  }
}
