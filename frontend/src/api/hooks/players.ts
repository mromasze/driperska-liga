import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type {
  CreatePlayerRequest,
  MatchSummary,
  PageResponse,
  Player,
  PlayersQuery,
  PlayerStats,
} from '../types';

/** GET /players */
export function usePlayers(query?: PlayersQuery) {
  return useQuery({
    queryKey: queryKeys.players(query),
    queryFn: () =>
      api.get<Player[]>('/players', {
        query: {
          active: query?.active,
          role: query?.role,
          search: query?.search,
        },
      }),
  });
}

/** GET /players/{id} */
export function usePlayer(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.player(id ?? ''),
    queryFn: () => api.get<Player>(`/players/${id}`),
    enabled: Boolean(id),
  });
}

/** GET /players/{id}/stats?season= */
export function usePlayerStats(id: string | undefined, season?: string) {
  return useQuery({
    queryKey: queryKeys.playerStats(id ?? '', season),
    queryFn: () => api.get<PlayerStats>(`/players/${id}/stats`, { query: { season } }),
    enabled: Boolean(id),
  });
}

/** GET /players/{id}/matches (paginated) */
export function usePlayerMatches(id: string | undefined, page = 0, size = 10) {
  return useQuery({
    queryKey: queryKeys.playerMatches(id ?? '', page),
    queryFn: () =>
      api.get<PageResponse<MatchSummary>>(`/players/${id}/matches`, { query: { page, size } }),
    enabled: Boolean(id),
  });
}

/** POST /players (ADMIN/EDITOR) */
export function useCreatePlayer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePlayerRequest) => api.post<Player>('/players', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['players'] }),
  });
}

/** POST /players/{id}/avatar (multipart) */
export function useUploadAvatar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => {
      const form = new FormData();
      form.append('file', file);
      return api.upload<Player>(`/players/${id}/avatar`, form);
    },
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['players'] });
      qc.invalidateQueries({ queryKey: queryKeys.player(variables.id) });
    },
  });
}
