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

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-changeit123}"

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="$(dirname "$LIB_DIR")"
BOTS_FILE="${BOTS_FILE:-$SCRIPTS_DIR/test-bots.json}"
HUMAN_FILE="${HUMAN_FILE:-$SCRIPTS_DIR/.human.txt}"

command -v jq   >/dev/null || { echo "jq is required (apt install jq)" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required"               >&2; exit 1; }

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
