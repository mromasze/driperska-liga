import { useQuery } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type { RankingRow } from '../types';

/** GET /ranking?season= — league leaderboard. */
export function useRanking(season?: string) {
  return useQuery({
    queryKey: queryKeys.ranking(season),
    queryFn: () => api.get<RankingRow[]>('/ranking', { query: { season } }),
  });
}
