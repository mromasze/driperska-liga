import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
          season: query?.season,
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
