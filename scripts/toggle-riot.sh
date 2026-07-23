#!/usr/bin/env bash
#
# toggle-riot.sh — reads or flips the admin "Riot API support" switch.
#
# For draft testing you want it OFF (accepting a squad starts the internal draft
# instead of creating a Riot lobby).
#
# Usage:
#   ./scripts/toggle-riot.sh            # show current value
#   ./scripts/toggle-riot.sh off        # disable Riot (draft mode) — recommended for tests
#   ./scripts/toggle-riot.sh on         # enable Riot lobby creation
#
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

admin_login
action="${1:-show}"

case "$action" in
  show)
    REQ GET /api/v1/admin/settings "" "$ADMIN_TOKEN"
    echo "Riot API: $(jq -r '.riotEnabled' <<<"$RESP" | sed 's/true/WŁĄCZONE (lobby Riot)/;s/false/WYŁĄCZONE (draft wewnętrzny)/')"
    ;;
  on|off)
    val=$([[ "$action" == "on" ]] && echo true || echo false)
    REQ PUT /api/v1/admin/settings "$(jq -n --argjson v "$val" '{riotEnabled:$v}')" "$ADMIN_TOKEN"
    [[ "$HTTP_CODE" == "200" ]] || { echo "✗ Nie udało się zapisać (HTTP $HTTP_CODE): $RESP" >&2; exit 1; }
    echo "✓ Riot API ustawione na: $(jq -r '.riotEnabled' <<<"$RESP")"
    [[ "$action" == "off" ]] && echo "  → Zaakceptowanie składu uruchomi teraz draft wewnętrzny."
    ;;
  *)
    echo "Użycie: $0 [show|on|off]" >&2; exit 1;;
esac
