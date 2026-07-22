import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type {
  ApproveRequest,
  CreateMatchRequest,
  DrawResult,
  MatchDetail,
  MatchEvent,
  MatchSummary,
  MatchesQuery,
  PageResponse,
  RejectRequest,
  ReplacePlayerRequest,
  RiotLobbyStatus,
  DrawLobby,
  SubmitResultsRequest,
} from '../types';

/** GET /matches (filter by status/season). */
export function useMatches(query?: MatchesQuery) {
  return useQuery({
    queryKey: queryKeys.matches(query),
    queryFn: () =>
      api.get<PageResponse<MatchSummary>>('/matches', {
        query: {
          status: query?.status,
          seasonId: query?.seasonId,
          page: query?.page,
          size: query?.size,
        },
      }),
  });
}

/** GET /matches/{id} — full detail + scoreboard. */
export function useMatch(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.match(id ?? ''),
    queryFn: () => api.get<MatchDetail>(`/matches/${id}`),
    enabled: Boolean(id),
    refetchInterval: (query) =>
      query.state.data?.status === 'LIVE' ? 10_000 : false,
  });
}

/** Fetch several match details at once (e.g. recent-results cards on the home page). */
export function useMatchDetails(ids: string[]) {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: queryKeys.match(id),
      queryFn: () => api.get<MatchDetail>(`/matches/${id}`),
      staleTime: 30_000,
    })),
  });
}

/** GET /matches/{id}/events — audit timeline (ADMIN/EDITOR). */
export function useMatchEvents(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.matchEvents(id ?? ''),
    queryFn: () => api.get<MatchEvent[]>(`/matches/${id}/events`),
    enabled: Boolean(id),
  });
}

/** POST /matches → DRAFT */
export function useCreateMatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateMatchRequest) => api.post<MatchDetail>('/matches', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['matches'] }),
  });
}

/** POST /matches/{id}/draw — draw / re-roll teams. */
export function useDrawTeams(matchId: string) {
  return useMutation({
    mutationFn: () => api.post<DrawResult>(`/matches/${matchId}/draw`),
  });
}

/** POST /matches/{id}/draw/confirm → LIVE */
export function useConfirmDraw(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<MatchDetail>(`/matches/${matchId}/draw/confirm`),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.match(matchId) }),
  });
}

/** POST /matches/{id}/results → RESULTS_SUBMITTED */
export function useSubmitResults(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SubmitResultsRequest) =>
      api.post<MatchDetail>(`/matches/${matchId}/results`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}

/** PATCH /matches/{id}/results — edit before approval. */
export function useEditResults(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SubmitResultsRequest) =>
      api.patch<MatchDetail>(`/matches/${matchId}/results`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.match(matchId) }),
  });
}

/**
 * POST /matches/{id}/approve → APPROVED (ADMIN).
 * On success invalidate ranking, matches and player profiles (docs/06 §6.7).
 */
export function useApproveMatch(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ApproveRequest) =>
      api.post<MatchDetail>(`/matches/${matchId}/approve`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      qc.invalidateQueries({ queryKey: ['matches'] });
      qc.invalidateQueries({ queryKey: ['ranking'] });
      qc.invalidateQueries({ queryKey: ['player'] });
    },
  });
}

/** POST /matches/{id}/reject → REJECTED (ADMIN). */
export function useRejectMatch(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RejectRequest) => api.post<MatchDetail>(`/matches/${matchId}/reject`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}

/** POST /matches/{id}/start ? admin starts the already-created Riot lobby. */
export function useStartMatch(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<MatchDetail>(`/matches/${matchId}/start`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}

/** POST /matches/{id}/start/manual — start without a Riot lobby (manually recorded match). */
export function useStartMatchManual(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<MatchDetail>(`/matches/${matchId}/start/manual`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}

/** GET /matches/{id}/draw-state — live vote tally + teams for the admin panel. */
export function useMatchDrawState(matchId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['matches', matchId, 'draw-state'],
    queryFn: () => api.get<DrawLobby>(`/matches/${matchId}/draw-state`),
    enabled,
    refetchInterval: enabled ? 2_000 : false,
    retry: false,
  });
}

export function useReplaceMatchPlayer(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ReplacePlayerRequest) =>
      api.post<MatchDetail>(`/matches/${matchId}/players/replace`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}

export function useRiotLobbyStatus(matchId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['matches', matchId, 'riot-lobby'],
    queryFn: () => api.get<RiotLobbyStatus>(`/matches/${matchId}/riot/lobby`),
    enabled,
    refetchInterval: enabled ? 10_000 : false,
    retry: false,
  });
}

export function useCancelMatch(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<MatchDetail>(`/matches/${matchId}/cancel`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}

export function useReopenMatch(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<MatchDetail>(`/matches/${matchId}/reopen`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}

export function useUploadReplay(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api.upload<MatchDetail>(`/matches/${matchId}/replay`, form);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.match(matchId) }),
  });
}

export function useShareMatchToDiscord(matchId: string) {
  return useMutation({
    mutationFn: () => api.post<{ sent: boolean; message: string }>(`/matches/${matchId}/share/discord`),
  });
}

export function useImportRiotResults(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<MatchDetail>(`/matches/${matchId}/riot/import`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}
