import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type {
  CreatePlayerRequest,
  PageResponse,
  Player,
  PlayerMatchEntry,
  PlayersQuery,
  PlayerStats,
  UpdatePlayerRequest,
} from '../types';

/** GET /players (paginated). */
export function usePlayers(query?: PlayersQuery) {
  return useQuery({
    queryKey: queryKeys.players(query),
    queryFn: () =>
      api.get<PageResponse<Player>>('/players', {
        query: {
          active: query?.active,
          role: query?.role,
          search: query?.search,
          page: query?.page,
          size: query?.size ?? 100,
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

/** GET /players/{id}/matches — approved match history (flat list). */
export function usePlayerMatches(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.playerMatches(id ?? ''),
    queryFn: () => api.get<PlayerMatchEntry[]>(`/players/${id}/matches`),
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

/** PATCH /players/{id} (ADMIN/EDITOR) */
export function useUpdatePlayer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdatePlayerRequest }) =>
      api.patch<Player>(`/players/${id}`, body),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['players'] });
      qc.invalidateQueries({ queryKey: queryKeys.player(variables.id) });
    },
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
