#!/usr/bin/env bash
#
# accept-squad.sh — logs in every mock bot from scripts/test-bots.json and casts
# an ACCEPT vote on their active draw, pushing the squad toward confirmation.
#
# The backend confirms the squad on the 6th ACCEPT and immediately creates the
# Riot tournament lobby. This script stops as soon as it sees the match reach
# LOBBY_READY and prints the tournament code.
#
# Usage:
#   ./scripts/accept-squad.sh            # all bots vote ACCEPT
#   ACCEPTS=5 ./scripts/accept-squad.sh  # only 5 bots vote (be the 6th yourself)
#
# Config (env vars, all optional):
#   BASE_URL   Backend base URL           (default: http://localhost:8080)
#   BOTS_FILE  Credentials file           (default: <script dir>/test-bots.json)
#   ACCEPTS    How many bots should vote  (default: all in the file)
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BOTS_FILE="${BOTS_FILE:-$SCRIPT_DIR/test-bots.json}"

command -v jq   >/dev/null || { echo "jq is required (apt install jq)" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required"               >&2; exit 1; }
[[ -f "$BOTS_FILE" ]] || { echo "✗ Nie znaleziono $BOTS_FILE — uruchom najpierw seed-test-players.sh" >&2; exit 1; }

TOTAL="$(jq 'length' "$BOTS_FILE")"
ACCEPTS="${ACCEPTS:-$TOTAL}"

# REQ METHOD PATH [BODY] [BEARER] -> sets globals RESP (body) and HTTP_CODE.
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

echo "→ ${ACCEPTS}/${TOTAL} botów zagłosuje ACCEPT na $BASE_URL"
echo

voted=0
for idx in $(seq 0 $(( ACCEPTS - 1 ))); do
  nick="$(jq -r ".[$idx].nickname" "$BOTS_FILE")"
  pass="$(jq -r ".[$idx].password" "$BOTS_FILE")"
  [[ "$nick" == "null" ]] && break

  # Log in.
  REQ POST /api/v1/auth/login "$(jq -n --arg u "$nick" --arg p "$pass" '{username:$u,password:$p}')"
  if [[ "$HTTP_CODE" != "200" ]]; then
    echo "✗ $nick — logowanie nie powiodło się (HTTP $HTTP_CODE)"; continue
  fi
  token="$(jq -r '.accessToken' <<<"$RESP")"

  # Find this bot's active draw.
  REQ GET /api/v1/draw-lobby/active "" "$token"
  if [[ "$HTTP_CODE" == "204" || -z "$RESP" ]]; then
    echo "· $nick — brak aktywnego losowania (czy mecz jest utworzony?)"; continue
  fi
  match_id="$(jq -r '.matchId' <<<"$RESP")"
  status="$(jq -r '.status' <<<"$RESP")"
  if [[ "$status" == "LOBBY_READY" ]]; then
    code="$(jq -r '.tournamentCode // "—"' <<<"$RESP")"
    echo; echo "✓ Skład już zatwierdzony. Kod lobby: $code"; exit 0
  fi
  if [[ "$status" != "TEAMS_DRAWN" ]]; then
    echo "· $nick — losowanie nie jest w fazie głosowania (status: $status)"; continue
  fi

  # Cast ACCEPT.
  REQ POST /api/v1/draw-lobby/vote "$(jq -n --arg m "$match_id" '{matchId:$m, decision:"ACCEPT"}')" "$token"
  if [[ "$HTTP_CODE" == "200" ]]; then
    voted=$(( voted + 1 ))
    accepts="$(jq -r '.accepts' <<<"$RESP")"
    new_status="$(jq -r '.status' <<<"$RESP")"
    echo "✓ $nick — ACCEPT (za: $accepts, status: $new_status)"
    if [[ "$new_status" == "LOBBY_READY" ]]; then
      code="$(jq -r '.tournamentCode // "—"' <<<"$RESP")"
      echo
      echo "🎉 Skład zatwierdzony — lobby Riot utworzone!"
      echo "   Kod turniejowy: $code"
      echo "   Panel gracza pokaże teraz stronę i kod. Admin uruchamia mecz w panelu."
      exit 0
    fi
  elif grep -q "został już oddany" <<<"$RESP"; then
    echo "· $nick — już głosował w tej rundzie"
  else
    echo "✗ $nick — głos odrzucony (HTTP $HTTP_CODE): $(jq -r '.message // .' <<<"$RESP" 2>/dev/null || echo "$RESP")"
    if [[ "$HTTP_CODE" == "503" || "$HTTP_CODE" == "500" ]]; then
      echo "  ↳ Jeśli to był 6. głos: sprawdź, czy wszyscy gracze (w tym Ty) mają realne Riot ID"
      echo "    i czy serwer ma ustawiony RIOT_API_KEY — lobby tworzy się właśnie na tym etapie."
    fi
  fi
done

echo
echo "→ Oddano $voted głosów ACCEPT. Potrzeba 6, aby zatwierdzić skład i utworzyć lobby."
