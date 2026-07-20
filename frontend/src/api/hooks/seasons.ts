import { useQuery } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type { RankingRow, Season } from '../types';

/** GET /seasons */
export function useSeasons() {
  return useQuery({
    queryKey: queryKeys.seasons,
    queryFn: () => api.get<Season[]>('/seasons'),
  });
}

/** GET /seasons/current — active season. */
export function useCurrentSeason() {
  return useQuery({
    queryKey: queryKeys.currentSeason,
    queryFn: () => api.get<Season>('/seasons/current'),
  });
}

/** GET /seasons/{id}/ranking */
export function useSeasonRanking(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.seasonRanking(id ?? ''),
    queryFn: () => api.get<RankingRow[]>(`/seasons/${id}/ranking`),
    enabled: Boolean(id),
  });
}
