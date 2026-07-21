import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type {
  CreatePlayerRequest, CreatedPlayerResponse, PageResponse, Player, PlayerMatchEntry,
  PlayersQuery, PlayerStats, SelfUpdatePlayerRequest, UpdatePlayerRequest,
} from '../types';

export function usePlayers(query?: PlayersQuery) {
  return useQuery({
    queryKey: queryKeys.players(query),
    queryFn: () => api.get<PageResponse<Player>>('/players', { query: {
      active: query?.active, role: query?.role, search: query?.search,
      page: query?.page, size: query?.size ?? 100,
    }}),
  });
}
export function usePlayer(id: string | undefined) {
  return useQuery({ queryKey: queryKeys.player(id ?? ''), queryFn: () => api.get<Player>(`/players/${id}`), enabled: Boolean(id) });
}
export function useMyPlayer() {
  return useQuery({ queryKey: queryKeys.myPlayer, queryFn: () => api.get<Player>('/players/me') });
}
export function usePlayerStats(id: string | undefined, season?: string) {
  return useQuery({ queryKey: queryKeys.playerStats(id ?? '', season), queryFn: () => api.get<PlayerStats>(`/players/${id}/stats`, { query: { season } }), enabled: Boolean(id) });
}
export function usePlayerMatches(id: string | undefined) {
  return useQuery({ queryKey: queryKeys.playerMatches(id ?? ''), queryFn: () => api.get<PlayerMatchEntry[]>(`/players/${id}/matches`), enabled: Boolean(id) });
}
export function useCreatePlayer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePlayerRequest) => api.post<CreatedPlayerResponse>('/players/with-account', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['players'] }),
  });
}
export function useProvisionPlayerAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.post<CreatedPlayerResponse>(`/players/${id}/account`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['players'] }),
  });
}
export function useUpdatePlayer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdatePlayerRequest }) => api.patch<Player>(`/players/${id}`, body),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['players'] });
      qc.invalidateQueries({ queryKey: queryKeys.player(variables.id) });
    },
  });
}
export function useUpdateMyPlayer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SelfUpdatePlayerRequest) => api.patch<Player>('/players/me', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.myPlayer }),
  });
}
export function useUploadAvatar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => {
      const form = new FormData(); form.append('file', file);
      return api.upload<Player>(`/players/${id}/avatar`, form);
    },
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['players'] });
      qc.invalidateQueries({ queryKey: queryKeys.player(variables.id) });
    },
  });
}
export function useUploadMyAvatar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData(); form.append('file', file);
      return api.upload<Player>('/players/me/avatar', form);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.myPlayer }),
  });
}