import type { MatchDetail, MatchParticipant, Role, Side } from '../api/types';

/** Lane order, top to bottom — how a lineup reads on a scoreboard. */
const ROLE_ORDER: Record<Role, number> = { TOP: 0, JUNGLE: 1, MID: 2, ADC: 3, SUPPORT: 4 };

export function teamOf(match: MatchDetail, side: Side): MatchParticipant[] {
  return match.participants.filter((p) => p.side === side);
}

/** One team, sorted into lane order so both sides line up row-for-row. */
export function lineupOf(match: MatchDetail, side: Side): MatchParticipant[] {
  return teamOf(match, side).sort((a, b) => ROLE_ORDER[a.role] - ROLE_ORDER[b.role]);
}

export function teamKills(match: MatchDetail, side: Side): number {
  return teamOf(match, side).reduce((sum, p) => sum + p.kills, 0);
}

/** The single highest-PR participant across the match (the match MVP). */
export function matchMvp(match: MatchDetail): MatchParticipant | null {
  const flagged = match.participants.find((p) => p.mvp);
  if (flagged) return flagged;
  let best: MatchParticipant | null = null;
  for (const p of match.participants) {
    const pr = p.performanceRating ?? -1;
    if (!best || pr > (best.performanceRating ?? -1)) best = p;
  }
  return best;
}
