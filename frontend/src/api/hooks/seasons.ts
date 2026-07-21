import { useQuery } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type { Season } from '../types';

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
