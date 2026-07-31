import { useQuery } from '@tanstack/react-query';
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
