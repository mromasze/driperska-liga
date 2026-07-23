#!/usr/bin/env bash
#
# start-draft.sh — admin starts the internal draft for a squad that's been confirmed (DRAFT_READY).
# Mirrors the "▶ Rozpocznij draft" button in the match control panel. Handy for fully-automated runs.
#
# Usage:  ./scripts/start-draft.sh
#
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
admin_login

REQ GET "/api/v1/matches?status=DRAFT_READY&size=10" "" "$ADMIN_TOKEN"
mid="$(jq -r '.content[0].id // empty' <<<"$RESP")"
[[ -n "$mid" ]] || { echo "✗ Brak meczu w stanie DRAFT_READY (najpierw zatwierdź skład: ./scripts/bots-vote.sh accept 6)." >&2; exit 1; }

REQ POST "/api/v1/draft/$mid/start" "" "$ADMIN_TOKEN"
if [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "204" ]]; then
  echo "✓ Draft rozpoczęty dla meczu $mid. Prowadź boty: ./scripts/draft-bots.sh"
else
  echo "✗ Nie udało się rozpocząć draftu (HTTP $HTTP_CODE): $RESP" >&2; exit 1
fi
