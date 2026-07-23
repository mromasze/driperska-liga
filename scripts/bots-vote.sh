#!/usr/bin/env bash
#
# bots-vote.sh — bots cast squad votes (ACCEPT or REJECT) on their active draw.
#
# 6 ACCEPT confirms the squad (→ DRAFTING when Riot is off, → LOBBY_READY when on).
# 5 REJECT forces a re-roll (new round, votes reset).
#
# Usage:
#   ./scripts/bots-vote.sh accept        # all bots vote ACCEPT
#   ./scripts/bots-vote.sh accept 5      # 5 bots ACCEPT — you cast the 6th yourself in the browser
#   ./scripts/bots-vote.sh reject 5      # 5 bots REJECT — force a re-roll
#
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
require_bots_file

decision_arg="${1:-accept}"
case "${decision_arg,,}" in
  accept) DECISION="ACCEPT";;
  reject) DECISION="REJECT";;
  *) echo "Użycie: $0 [accept|reject] [liczba]" >&2; exit 1;;
esac

TOTAL="$(jq 'length' "$BOTS_FILE")"
COUNT="${2:-$TOTAL}"

echo "→ ${COUNT}/${TOTAL} botów zagłosuje ${DECISION} na $BASE_URL"; echo

voted=0
for idx in $(seq 0 $(( COUNT - 1 ))); do
  nick="$(jq -r ".[$idx].nickname" "$BOTS_FILE")"; pass="$(jq -r ".[$idx].password" "$BOTS_FILE")"
  [[ "$nick" == "null" ]] && break
  token="$(login "$nick" "$pass")" || { echo "✗ $nick — logowanie (HTTP $HTTP_CODE)"; continue; }

  REQ GET /api/v1/draw-lobby/active "" "$token"
  [[ "$HTTP_CODE" == "204" || -z "$RESP" || "$RESP" == "null" ]] && { echo "· $nick — brak aktywnego losowania"; continue; }
  match_id="$(jq -r '.matchId' <<<"$RESP")"; status="$(jq -r '.status' <<<"$RESP")"
  if [[ "$status" != "TEAMS_DRAWN" ]]; then echo "· $nick — nie faza głosowania (status: $status)"; continue; fi

  REQ POST /api/v1/draw-lobby/vote "$(jq -n --arg m "$match_id" --arg d "$DECISION" '{matchId:$m, decision:$d}')" "$token"
  if [[ "$HTTP_CODE" == "200" ]]; then
    voted=$(( voted + 1 ))
    new_status="$(jq -r '.status' <<<"$RESP")"; accepts="$(jq -r '.accepts' <<<"$RESP")"; rejects="$(jq -r '.rejects' <<<"$RESP")"
    echo "✓ $nick — $DECISION (za: $accepts, przeciw: $rejects, status: $new_status)"
    if [[ "$new_status" == "DRAFT_READY" ]]; then
      echo; echo "🎯 Skład zatwierdzony. Admin rozpoczyna draft (panel: Kontrola meczu → ▶ Rozpocznij draft"
      echo "   albo: ./scripts/start-draft.sh), a boty prowadź: ./scripts/draft-bots.sh (zostaw działające)."; exit 0
    elif [[ "$new_status" == "DRAFTING" ]]; then
      echo; echo "🎯 DRAFT wystartował! Uruchom: ./scripts/draft-bots.sh"; exit 0
    elif [[ "$new_status" == "LOBBY_READY" ]]; then
      echo; echo "🎉 Skład zatwierdzony — lobby Riot (kod: $(jq -r '.tournamentCode // "—"' <<<"$RESP"))."; exit 0
    fi
  elif grep -q "został już oddany" <<<"$RESP"; then echo "· $nick — już głosował"
  else echo "✗ $nick — odrzucono (HTTP $HTTP_CODE): $(jq -r '.message // .' <<<"$RESP" 2>/dev/null)"; fi
done

echo; echo "→ Oddano $voted głosów ${DECISION}. (ACCEPT: 6 zatwierdza · REJECT: 5 losuje ponownie)"
