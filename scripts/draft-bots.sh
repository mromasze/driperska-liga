#!/usr/bin/env bash
#
# draft-bots.sh — drives the champion draft for the bots. Runs a watch loop: every
# tick it checks each bot's turn and, when it's theirs, bans (if captain) or picks a
# random available champion. Your own turns are left for YOU to do in the browser —
# the bots wait for you on your team's pick steps.
#
# Leave it running from the moment the draft starts until it prints "draft zakończony".
#
# Usage:
#   ./scripts/draft-bots.sh              # watch loop until the draft is done
#   ONCE=1 ./scripts/draft-bots.sh       # single pass (act once, then exit)
#
# Config (env vars):
#   INTERVAL      Seconds between ticks        (default: 2)
#   MAX_SECONDS   Safety timeout               (default: 900)
#   HUMAN_FILE    File with your player UUID    (written by create-match.sh)
#   + BASE_URL / BOTS_FILE (see lib/common.sh)
#
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
require_bots_file

INTERVAL="${INTERVAL:-2}"
MAX_SECONDS="${MAX_SECONDS:-900}"
HUMAN_ID=""; [[ -f "$HUMAN_FILE" ]] && HUMAN_ID="$(tr -d '[:space:]' < "$HUMAN_FILE")"

# Log in every bot once; map playerId → token and playerId → nick.
declare -A TOKEN NICK
N="$(jq 'length' "$BOTS_FILE")"
for idx in $(seq 0 $(( N - 1 ))); do
  nick="$(jq -r ".[$idx].nickname" "$BOTS_FILE")"; pass="$(jq -r ".[$idx].password" "$BOTS_FILE")"
  pid="$(jq -r ".[$idx].playerId" "$BOTS_FILE")"
  t="$(login "$nick" "$pass")" || { echo "✗ $nick — logowanie (HTTP $HTTP_CODE)"; continue; }
  TOKEN[$pid]="$t"; NICK[$pid]="$nick"
done
[[ ${#TOKEN[@]} -gt 0 ]] || { echo "✗ Żaden bot się nie zalogował." >&2; exit 1; }

# Champion id universe (fetched once with any bot token).
any_token="$(printf '%s\n' "${TOKEN[@]}" | head -n1)"
REQ GET /api/v1/champions "" "$any_token"
mapfile -t CHAMP_IDS < <(jq -r '.[].id' <<<"$RESP")
[[ ${#CHAMP_IDS[@]} -gt 0 ]] || { echo "✗ Brak listy postaci (/champions)." >&2; exit 1; }
echo "→ ${#TOKEN[@]} botów zalogowanych, ${#CHAMP_IDS[@]} postaci. Prowadzę draft…"
[[ -n "$HUMAN_ID" ]] && echo "  (Twoje pole zostawiam Tobie — boty poczekają na Twój pick.)"

pick_random_available() {  # $1 = state json → prints a champion id not yet used
  local state="$1"; declare -A used=(); local id
  while IFS= read -r id; do [[ -n "$id" ]] && used[$id]=1; done < <(
    jq -r '([.draft.blueBans[]?, .draft.redBans[]?] + [ (.blue[],.red[]) | .championId | numbers ]) | .[]' <<<"$state")
  local -a avail=(); for id in "${CHAMP_IDS[@]}"; do [[ -z "${used[$id]:-}" ]] && avail+=("$id"); done
  [[ ${#avail[@]} -eq 0 ]] && return 1
  echo "${avail[$(( RANDOM % ${#avail[@]} ))]}"
}

start_ts=$SECONDS
while :; do
  progressed=0; done=0
  for pid in "${!TOKEN[@]}"; do
    token="${TOKEN[$pid]}"
    REQ GET /api/v1/draw-lobby/active "" "$token"
    [[ "$HTTP_CODE" == "204" || -z "$RESP" || "$RESP" == "null" ]] && continue
    state="$RESP"
    status="$(jq -r '.status' <<<"$state")"
    if [[ "$status" == "DRAFTED" ]]; then done=1; continue; fi
    [[ "$status" != "DRAFTING" ]] && continue
    match_id="$(jq -r '.matchId' <<<"$state")"
    cs="$(jq -r '.draft.currentSide // empty' <<<"$state")"
    ct="$(jq -r '.draft.currentType // empty' <<<"$state")"
    [[ -z "$cs" || -z "$ct" ]] && continue

    myside="$(jq -r --arg me "$pid" '((.blue[],.red[]) | select(.playerId==$me) | .side) // empty' <<<"$state")"
    mychamp="$(jq -r --arg me "$pid" '((.blue[],.red[]) | select(.playerId==$me) | .championId) // "null"' <<<"$state")"
    [[ "$myside" != "$cs" ]] && continue

    if [[ "$ct" == "BAN" ]]; then
      captain="$(jq -r --arg s "$cs" 'if $s=="BLUE" then .draft.blueCaptain else .draft.redCaptain end' <<<"$state")"
      [[ "$pid" != "$captain" ]] && continue
      champ="$(pick_random_available "$state")" || continue
      REQ POST "/api/v1/draft/$match_id/ban" "$(jq -n --argjson c "$champ" '{championId:$c}')" "$token"
      [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "204" ]] && { echo "🚫 ${NICK[$pid]} (kapitan $cs) banuje #$champ"; progressed=1; }
    else # PICK
      [[ "$mychamp" != "null" ]] && continue
      # Defer to the human: if you're on this side and haven't picked, bots wait.
      if [[ -n "$HUMAN_ID" && "$pid" != "$HUMAN_ID" ]]; then
        h_side="$(jq -r --arg me "$HUMAN_ID" '((.blue[],.red[]) | select(.playerId==$me) | .side) // empty' <<<"$state")"
        h_champ="$(jq -r --arg me "$HUMAN_ID" '((.blue[],.red[]) | select(.playerId==$me) | .championId) // "null"' <<<"$state")"
        [[ "$h_side" == "$cs" && "$h_champ" == "null" ]] && continue
      fi
      champ="$(pick_random_available "$state")" || continue
      REQ POST "/api/v1/draft/$match_id/pick" "$(jq -n --argjson c "$champ" '{championId:$c}')" "$token"
      [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "204" ]] && { echo "✅ ${NICK[$pid]} ($cs) wybiera #$champ"; progressed=1; }
    fi
  done

  if [[ "$done" == "1" ]]; then
    echo; echo "🏁 Draft zakończony. Sprawdź panel — możesz testować swapy: ./scripts/bots-accept-swaps.sh"
    break
  fi
  [[ -n "${ONCE:-}" ]] && break
  if (( SECONDS - start_ts > MAX_SECONDS )); then echo "⏱ Limit czasu ($MAX_SECONDS s) — kończę."; break; fi
  sleep "$INTERVAL"
done
