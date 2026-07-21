import type { MatchDetail, MatchParticipant, Side } from '../api/types';

export function teamOf(match: MatchDetail, side: Side): MatchParticipant[] {
  return match.participants.filter((p) => p.side === side);
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
