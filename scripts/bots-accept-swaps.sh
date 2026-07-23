#!/usr/bin/env bash
#
# bots-accept-swaps.sh — after the draft, bots auto-accept any swap request sent to
# them. So in the browser you (a human player) click the ⇄ arrow on a bot teammate,
# choose "zamień pozycję" or "zamień postać", and this script makes the bot accept.
#
# Usage:
#   ./scripts/bots-accept-swaps.sh          # watch loop, accept incoming swaps
#   ONCE=1 ./scripts/bots-accept-swaps.sh   # single pass
#
# Config: INTERVAL (default 2), MAX_SECONDS (default 600) + BASE_URL / BOTS_FILE.
#
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
require_bots_file

INTERVAL="${INTERVAL:-2}"; MAX_SECONDS="${MAX_SECONDS:-600}"

declare -A TOKEN NICK
N="$(jq 'length' "$BOTS_FILE")"
for idx in $(seq 0 $(( N - 1 ))); do
  nick="$(jq -r ".[$idx].nickname" "$BOTS_FILE")"; pass="$(jq -r ".[$idx].password" "$BOTS_FILE")"
  pid="$(jq -r ".[$idx].playerId" "$BOTS_FILE")"
  t="$(login "$nick" "$pass")" || continue
  TOKEN[$pid]="$t"; NICK[$pid]="$nick"
done
[[ ${#TOKEN[@]} -gt 0 ]] || { echo "✗ Żaden bot się nie zalogował." >&2; exit 1; }
echo "→ Boty czekają na Twoje prośby o zamianę (⇄ w przeglądarce)…"

start_ts=$SECONDS
while :; do
  for pid in "${!TOKEN[@]}"; do
    token="${TOKEN[$pid]}"
    REQ GET /api/v1/draw-lobby/active "" "$token"
    [[ "$HTTP_CODE" == "204" || -z "$RESP" || "$RESP" == "null" ]] && continue
    match_id="$(jq -r '.matchId' <<<"$RESP")"
    # Swap requests addressed to this bot.
    while IFS=$'\t' read -r sid stype; do
      [[ -z "$sid" ]] && continue
      REQ POST "/api/v1/draft/$match_id/swap/$sid/accept" "" "$token"
      [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "204" ]] \
        && echo "🔁 ${NICK[$pid]} akceptuje zamianę ($stype)"
    done < <(jq -r --arg me "$pid" '.draft.swaps[]? | select(.toPlayerId==$me) | "\(.id)\t\(.type)"' <<<"$RESP")
  done
  [[ -n "${ONCE:-}" ]] && break
  (( SECONDS - start_ts > MAX_SECONDS )) && { echo "⏱ Limit czasu — kończę."; break; }
  sleep "$INTERVAL"
done
