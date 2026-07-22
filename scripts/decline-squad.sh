#!/usr/bin/env bash
#
# decline-squad.sh — logs in the mock bots from scripts/test-bots.json and casts a REJECT vote,
# to test the re-roll path. The backend re-draws the teams once REJECT votes reach the threshold
# (5 of 10), starting a fresh round. This script detects and reports that re-roll.
#
# Usage:
#   ./scripts/decline-squad.sh            # all bots vote REJECT
#   DECLINES=5 ./scripts/decline-squad.sh # only 5 bots vote (exactly enough to force a re-roll)
#
# Config (env vars, all optional):
#   BASE_URL   Backend base URL           (default: http://localhost:8080)
#   BOTS_FILE  Credentials file           (default: <script dir>/test-bots.json)
#   DECLINES   How many bots should vote  (default: all in the file)
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BOTS_FILE="${BOTS_FILE:-$SCRIPT_DIR/test-bots.json}"

command -v jq   >/dev/null || { echo "jq is required (apt install jq)" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required"               >&2; exit 1; }
[[ -f "$BOTS_FILE" ]] || { echo "✗ Nie znaleziono $BOTS_FILE — uruchom najpierw seed-test-players.sh" >&2; exit 1; }

TOTAL="$(jq 'length' "$BOTS_FILE")"
DECLINES="${DECLINES:-$TOTAL}"

RESP=""; HTTP_CODE=""
REQ() {
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-sS -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body"  ]] && args+=(-d "$body")
  local out; out="$(curl "${args[@]}" -w '%{http_code}' 2>/dev/null || true)"
  HTTP_CODE="${out: -3}"
  RESP="${out:0:${#out}-3}"
}

echo "→ ${DECLINES}/${TOTAL} botów zagłosuje REJECT na $BASE_URL"
echo

prev_round=""
voted=0
for idx in $(seq 0 $(( DECLINES - 1 ))); do
  nick="$(jq -r ".[$idx].nickname" "$BOTS_FILE")"
  pass="$(jq -r ".[$idx].password" "$BOTS_FILE")"
  [[ "$nick" == "null" ]] && break

  REQ POST /api/v1/auth/login "$(jq -n --arg u "$nick" --arg p "$pass" '{username:$u,password:$p}')"
  if [[ "$HTTP_CODE" != "200" ]]; then
    echo "✗ $nick — logowanie nie powiodło się (HTTP $HTTP_CODE)"; continue
  fi
  token="$(jq -r '.accessToken' <<<"$RESP")"

  REQ GET /api/v1/draw-lobby/active "" "$token"
  if [[ "$HTTP_CODE" == "204" || -z "$RESP" ]]; then
    echo "· $nick — brak aktywnego losowania (czy mecz jest utworzony?)"; continue
  fi
  match_id="$(jq -r '.matchId' <<<"$RESP")"
  status="$(jq -r '.status' <<<"$RESP")"
  round="$(jq -r '.round' <<<"$RESP")"
  [[ -z "$prev_round" ]] && prev_round="$round"
  if [[ "$status" != "TEAMS_DRAWN" ]]; then
    echo "· $nick — losowanie nie jest w fazie głosowania (status: $status)"; continue
  fi

  REQ POST /api/v1/draw-lobby/vote "$(jq -n --arg m "$match_id" '{matchId:$m, decision:"REJECT"}')" "$token"
  if [[ "$HTTP_CODE" == "200" ]]; then
    voted=$(( voted + 1 ))
    rejects="$(jq -r '.rejects' <<<"$RESP")"
    new_round="$(jq -r '.round' <<<"$RESP")"
    echo "✓ $nick — REJECT (przeciw: $rejects, runda: $new_round)"
    if [[ "$new_round" != "$prev_round" ]]; then
      echo
      echo "🔄 Przekroczono próg — drużyny wylosowane od nowa (runda $prev_round → $new_round)."
      echo "   Głosy zresetowane. Uruchom ponownie, aby znów odrzucić, lub accept-squad.sh, aby zaakceptować."
      exit 0
    fi
  elif grep -q "został już oddany" <<<"$RESP"; then
    echo "· $nick — już głosował w tej rundzie"
  else
    echo "✗ $nick — głos odrzucony (HTTP $HTTP_CODE): $(jq -r '.message // .' <<<"$RESP" 2>/dev/null || echo "$RESP")"
  fi
done

echo
echo "→ Oddano $voted głosów REJECT. Potrzeba 5, aby wymusić ponowne losowanie."
