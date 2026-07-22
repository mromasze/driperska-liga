import { useQuery } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type { Champion } from '../types';

/**
 * GET /champions — rarely changes, so cache it aggressively (docs/07 §7.4).
 * Used by the ChampionPicker and to resolve champion metadata on scoreboards.
 */
export function useChampions() {
  return useQuery({
    queryKey: queryKeys.champions,
    queryFn: () => api.get<Champion[]>('/champions'),
    staleTime: 1000 * 60 * 60, // 1h — champion data is effectively static
  });
}
