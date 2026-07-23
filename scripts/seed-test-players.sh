#!/usr/bin/env bash
#
# seed-test-players.sh — creates 9 mock players (you are the 10th) with portal
# accounts, and records their login credentials to scripts/test-bots.json so
# accept-squad.sh can log them in later.
#
# Re-runnable: if a bot already exists, its password is regenerated (via the
# resend/provision endpoints) so the saved file always holds a working password.
#
# Usage:
#   ./scripts/seed-test-players.sh
#
# Config (env vars, all optional):
#   BASE_URL       Backend base URL          (default: http://localhost:8080)
#   ADMIN_USER     Admin login               (default: admin)
#   ADMIN_PASS     Admin password            (default: changeit123)
#   BOT_COUNT      How many bots to create   (default: 9)
#   BOTS_FILE      Where to save credentials (default: <script dir>/test-bots.json)
#   RIOT_IDS_FILE  Optional file with one real "Name#TAG" per line, assigned to
#                  bots in order. REQUIRED for real lobby creation — see note below.
#
# NOTE on Riot IDs: creating players works with or without Riot IDs, but the
# moment the squad is confirmed (6th ACCEPT) the backend calls the Riot API to
# resolve every player's PUUID. Bots without a real, resolvable "Name#TAG" will
# make that step fail. To test the full flow through lobby creation, drop 9 real
# (smurf) Riot IDs into scripts/riot-ids.txt and set RIOT_API_KEY on the server.
#
set -euo pipefail

# Shared helpers + local backend auto-detection (BASE_URL, REQ, jq/curl checks).
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

BOT_COUNT="${BOT_COUNT:-9}"
SCRIPT_DIR="$SCRIPTS_DIR"
RIOT_IDS_FILE="${RIOT_IDS_FILE:-$SCRIPT_DIR/riot-ids.txt}"

ROLES=(TOP JUNGLE MID ADC SUPPORT)

echo "→ Logowanie jako admin ($ADMIN_USER) na $BASE_URL"
TOKEN="$(login "$ADMIN_USER" "$ADMIN_PASS")" \
  || { echo "✗ Logowanie admina nie powiodło się (HTTP $HTTP_CODE): $RESP" >&2; exit 1; }
[[ -n "$TOKEN" && "$TOKEN" != "null" ]] || { echo "✗ Brak accessToken w odpowiedzi" >&2; exit 1; }

# Optional real Riot IDs, one per line.
RIOT_IDS=()
if [[ -f "$RIOT_IDS_FILE" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="$(echo "$line" | xargs || true)"
    [[ -n "$line" && "${line:0:1}" != "#" ]] && RIOT_IDS+=("$line")
  done < "$RIOT_IDS_FILE"
  echo "→ Wczytano ${#RIOT_IDS[@]} Riot ID z $RIOT_IDS_FILE"
else
  echo "⚠ Brak $RIOT_IDS_FILE — boty powstaną bez Riot ID (lobby przy 6. głosie się nie utworzy)."
fi

results='[]'
for i in $(seq 1 "$BOT_COUNT"); do
  nick="TestBot$i"
  role="${ROLES[$(( (i-1) % ${#ROLES[@]} ))]}"
  riot_id="${RIOT_IDS[$((i-1))]:-}"
  discord="testbot$i"

  if [[ -n "$riot_id" ]]; then
    body="$(jq -n --arg n "$nick" --arg r "$role" --arg d "$discord" --arg ri "$riot_id" \
      '{nickname:$n, mainRole:$r, discordName:$d, riotId:$ri}')"
  else
    body="$(jq -n --arg n "$nick" --arg r "$role" --arg d "$discord" \
      '{nickname:$n, mainRole:$r, discordName:$d}')"
  fi

  REQ POST /api/v1/players/with-account "$body" "$TOKEN"
  if [[ "$HTTP_CODE" == "201" || "$HTTP_CODE" == "200" ]]; then
    echo "✓ $nick — utworzony"
  else
    # Likely already exists — resolve id and regenerate a fresh password.
    REQ GET "/api/v1/players?search=$nick&size=50" "" "$TOKEN"
    pid="$(jq -r --arg n "$nick" '.content[]? | select(.nickname|ascii_downcase == ($n|ascii_downcase)) | .id' <<<"$RESP" | head -n1)"
    if [[ -z "$pid" || "$pid" == "null" ]]; then
      echo "✗ $nick — nie udało się utworzyć (HTTP $HTTP_CODE)" >&2; exit 1
    fi
    if [[ -n "$riot_id" ]]; then
      REQ PATCH "/api/v1/players/$pid" \
        "$(jq -n --arg ri "$riot_id" --arg d "$discord" '{riotId:$ri, discordName:$d}')" "$TOKEN"
    fi
    REQ POST "/api/v1/players/$pid/credentials/resend" "" "$TOKEN"
    if [[ "$HTTP_CODE" != "200" ]]; then
      REQ POST "/api/v1/players/$pid/account" "" "$TOKEN"   # no account yet → provision
    fi
    [[ "$HTTP_CODE" == "200" ]] || { echo "✗ $nick — nie udało się odświeżyć hasła (HTTP $HTTP_CODE): $RESP" >&2; exit 1; }
    echo "✓ $nick — istniał, hasło odświeżone"
  fi

  pid="$(jq -r '.player.id' <<<"$RESP")"
  pass="$(jq -r '.credentials.temporaryPassword' <<<"$RESP")"
  login="$(jq -r '.credentials.login' <<<"$RESP")"
  results="$(jq \
    --arg nick "$login" --arg pass "$pass" --arg pid "$pid" --arg ri "$riot_id" \
    '. += [{nickname:$nick, password:$pass, playerId:$pid, riotId:(if $ri=="" then null else $ri end)}]' \
    <<<"$results")"
done

echo "$results" > "$BOTS_FILE"
chmod 600 "$BOTS_FILE" 2>/dev/null || true

echo
echo "✓ Gotowe. Zapisano ${BOT_COUNT} botów do: $BOTS_FILE"
echo
echo "Identyfikatory graczy (zaznacz je + siebie przy tworzeniu meczu w panelu admina):"
jq -r '.[] | "  \(.nickname)  →  \(.playerId)"' <<<"$results"
echo
echo "Następny krok: stwórz mecz w panelu admina z tymi 9 botami + swoim kontem (10 graczy),"
echo "a gdy status będzie TEAMS_DRAWN uruchom: ./scripts/accept-squad.sh"
