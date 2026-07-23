#!/usr/bin/env bash
#
# reset-matches.sh — cancels every non-finished match so you can start a clean test
# run. Cancels matches in DRAFT / TEAMS_DRAWN / DRAFTING / DRAFTED / LOBBY_READY /
# LIVE / RESULTS_SUBMITTED / REJECTED. Approved matches are left untouched.
#
# Usage:  ./scripts/reset-matches.sh
#
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
admin_login

STATUSES=(DRAFT TEAMS_DRAWN DRAFTING DRAFTED LOBBY_READY LIVE RESULTS_SUBMITTED REJECTED)
cancelled=0
for st in "${STATUSES[@]}"; do
  REQ GET "/api/v1/matches?status=$st&size=100" "" "$ADMIN_TOKEN"
  [[ "$HTTP_CODE" == "200" ]] || continue
  while IFS= read -r mid; do
    [[ -z "$mid" || "$mid" == "null" ]] && continue
    REQ POST "/api/v1/matches/$mid/cancel" "" "$ADMIN_TOKEN"
    if [[ "$HTTP_CODE" == "200" ]]; then echo "✓ Anulowano $mid ($st)"; cancelled=$(( cancelled + 1 ))
    else echo "· $mid ($st) — nie anulowano (HTTP $HTTP_CODE)"; fi
  done < <(jq -r '.content[]?.id' <<<"$RESP")
done
rm -f "$HUMAN_FILE" 2>/dev/null || true
echo; echo "→ Gotowe. Anulowano $cancelled meczów. Możesz stworzyć nowy: ./scripts/create-match.sh"
