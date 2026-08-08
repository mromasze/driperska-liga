import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type { LeagueSummary, RankingRow } from '../types';

/** GET /ranking?season= — league leaderboard. */
export function useRanking(season?: string) {
  return useQuery({
    queryKey: queryKeys.ranking(season),
    queryFn: () => api.get<RankingRow[]>('/ranking', { query: { season } }),
  });
}

/**
 * GET /ranking/summary?season= — season totals (kills, deaths, play time…). Only worth fetching once
 * a season id is known, otherwise the backend would answer for whatever season is active now.
 */
export function useLeagueSummary(season?: string) {
  return useQuery({
    queryKey: queryKeys.leagueSummary(season),
    queryFn: () => api.get<LeagueSummary>('/ranking/summary', { query: { season } }),
    enabled: Boolean(season),
    staleTime: 60_000,
  });
}

/**
 * POST /ranking/recalculate (ADMIN) — rebuilds the season from its approved matches.
 *
 * The endpoint existed from the start but nothing in the panel called it, so a scoring rule that
 * changed left every already-approved match carrying the numbers of the old rule. Everything derived
 * from a match is rewritten here — LP, PR, MMR, MVP/ACE marks and the season table — so the caches for
 * matches, ranking and player profiles all have to go.
 */
export function useRecalculateRanking() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (season?: string) =>
      api.post<{ seasonId: string }>('/ranking/recalculate', undefined, { query: { season } }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['ranking'] });
      void qc.invalidateQueries({ queryKey: ['matches'] });
      void qc.invalidateQueries({ queryKey: ['match'] });
      void qc.invalidateQueries({ queryKey: ['player'] });
    },
  });
}
