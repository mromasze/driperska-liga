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

# Shared helpers + local backend auto-detection (BASE_URL, REQ, jq/curl checks).
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
require_bots_file

TOTAL="$(jq 'length' "$BOTS_FILE")"
DECLINES="${DECLINES:-$TOTAL}"

echo "→ ${DECLINES}/${TOTAL} botów zagłosuje REJECT na $BASE_URL"
echo

prev_round=""
voted=0
for idx in $(seq 0 $(( DECLINES - 1 ))); do
  nick="$(jq -r ".[$idx].nickname" "$BOTS_FILE")"
  pass="$(jq -r ".[$idx].password" "$BOTS_FILE")"
  [[ "$nick" == "null" ]] && break

  token="$(login "$nick" "$pass")" || { echo "✗ $nick — logowanie nie powiodło się (HTTP $HTTP_CODE)"; continue; }

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
