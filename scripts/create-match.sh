#!/usr/bin/env bash
#
# create-match.sh — admin creates a fresh match from your player + the mock bots,
# which immediately draws teams (status TEAMS_DRAWN, voting opens).
#
# The match needs exactly 10 players: YOU + 9 bots from test-bots.json. Your player
# defaults to "mromasze" (the account you operate in the browser). Override with
# HUMAN_PLAYER, or set HUMAN_PLAYER="" for a fully-automatable 10-bot match.
#
# Usage:
#   ./scripts/create-match.sh                        # mromasze + 9 bots
#   HUMAN_PLAYER="InnyNick" ./scripts/create-match.sh
#   HUMAN_PLAYER="" ./scripts/create-match.sh         # 10 bots (seed BOT_COUNT=10 first)
#
# Config (env vars):
#   HUMAN_PLAYER  Your player's nickname or UUID  (default: mromasze; "" = all bots)
#   DRAW_MODE     PURE_RANDOM | BALANCED | MANUAL  (default: PURE_RANDOM)
#   + BASE_URL / ADMIN_USER / ADMIN_PASS / BOTS_FILE (see lib/common.sh)
#
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
require_bots_file
admin_login

# Default to your own player; HUMAN_PLAYER="" (explicitly empty) means all-bots.
HUMAN_PLAYER="${HUMAN_PLAYER-mromasze}"
DRAW_MODE="${DRAW_MODE:-PURE_RANDOM}"

# Resolve the human player id (if provided) and remember it for the draft driver.
: > "$HUMAN_FILE"
human_id=""
if [[ -n "$HUMAN_PLAYER" ]]; then
  if [[ "$HUMAN_PLAYER" =~ ^[0-9a-fA-F-]{36}$ ]]; then
    human_id="$HUMAN_PLAYER"
  else
    REQ GET "/api/v1/players?search=$HUMAN_PLAYER&size=50" "" "$ADMIN_TOKEN"
    human_id="$(jq -r --arg n "$HUMAN_PLAYER" \
      '.content[]? | select(.nickname|ascii_downcase == ($n|ascii_downcase)) | .id' <<<"$RESP" | head -n1)"
  fi
  [[ -n "$human_id" && "$human_id" != "null" ]] \
    || { echo "✗ Nie znaleziono gracza '$HUMAN_PLAYER'. Sprawdź nick albo podaj UUID." >&2; exit 1; }
  echo "$human_id" > "$HUMAN_FILE"
  echo "→ Twój gracz: $HUMAN_PLAYER ($human_id)"
fi

# Build the 10-player pool: human first (if any), then bots, capped at 10.
mapfile -t bot_ids < <(jq -r '.[].playerId' "$BOTS_FILE")
pool=()
[[ -n "$human_id" ]] && pool+=("$human_id")
for id in "${bot_ids[@]}"; do
  [[ "$id" == "$human_id" ]] && continue
  pool+=("$id")
  [[ "${#pool[@]}" -ge 10 ]] && break
done

if [[ "${#pool[@]}" -ne 10 ]]; then
  echo "✗ Potrzeba dokładnie 10 graczy, mam ${#pool[@]}." >&2
  echo "  Zaseeduj więcej botów (np. BOT_COUNT=10 ./scripts/seed-test-players.sh) lub podaj HUMAN_PLAYER." >&2
  exit 1
fi

REQ GET /api/v1/seasons/current "" "$ADMIN_TOKEN"
season_id="$(jq -r '.id' <<<"$RESP")"
[[ -n "$season_id" && "$season_id" != "null" ]] || { echo "✗ Brak aktywnego sezonu ($RESP)" >&2; exit 1; }

ids_json="$(printf '%s\n' "${pool[@]}" | jq -R . | jq -s .)"
body="$(jq -n --arg s "$season_id" --arg dm "$DRAW_MODE" --argjson ids "$ids_json" \
  '{seasonId:$s, drawMode:$dm, playerIds:$ids}')"

REQ POST /api/v1/matches "$body" "$ADMIN_TOKEN"
[[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "201" ]] \
  || { echo "✗ Tworzenie meczu nie powiodło się (HTTP $HTTP_CODE): $RESP" >&2; exit 1; }

match_id="$(jq -r '.id' <<<"$RESP")"
status="$(jq -r '.status' <<<"$RESP")"
echo "✓ Mecz utworzony: $match_id (status: $status, tryb: $DRAW_MODE)"
echo
echo "Dalej:"
echo "  • Głosowanie: ./scripts/bots-vote.sh accept 5   (5 botów za — Ty oddajesz 6. głos w przeglądarce)"
echo "  • Albo od razu draft: ./scripts/bots-vote.sh accept 6   (start draftu bez Twojego głosu)"
echo "  • Prowadzenie draftu przez boty: ./scripts/draft-bots.sh"
