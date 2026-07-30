import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type {
  CreateSubmissionRequest,
  MatchDetail,
  PageResponse,
  Submission,
  SubmitResultsRequest,
  UpdateSubmissionRequest,
} from '../types';

/**
 * The moderator panel talks to `/moderation/matches` — its own endpoint, scoped to the submissions
 * the logged-in moderator created. The admin match endpoints stay out of reach.
 */

/** GET /moderation/matches — my submissions, newest played first. */
export function useMySubmissions(enabled = true) {
  return useQuery({
    queryKey: queryKeys.mySubmissions,
    queryFn: () => api.get<PageResponse<Submission>>('/moderation/matches', { query: { size: 50 } }),
    enabled,
  });
}

/** POST /moderation/matches — roster + date of a played match. Statistics come next. */
export function useCreateSubmission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateSubmissionRequest) =>
      api.post<MatchDetail>('/moderation/matches', body),
    onSuccess: (match) => {
      qc.setQueryData(queryKeys.match(match.id), match);
      void qc.invalidateQueries({ queryKey: queryKeys.mySubmissions });
    },
  });
}

/** PATCH /moderation/matches/{id} — fix the date, or the roster while nothing is queued yet. */
export function useUpdateSubmission(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateSubmissionRequest) =>
      api.patch<MatchDetail>(`/moderation/matches/${matchId}`, body),
    onSuccess: (match) => {
      qc.setQueryData(queryKeys.match(matchId), match);
      void qc.invalidateQueries({ queryKey: queryKeys.mySubmissions });
    },
  });
}

/** POST /moderation/matches/{id}/results — send to (or update inside) the approval queue. */
export function useSubmitSubmissionResults(matchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SubmitResultsRequest) =>
      api.post<MatchDetail>(`/moderation/matches/${matchId}/results`, body),
    onSuccess: (match) => {
      qc.setQueryData(queryKeys.match(matchId), match);
      void qc.invalidateQueries({ queryKey: queryKeys.match(matchId) });
      void qc.invalidateQueries({ queryKey: queryKeys.mySubmissions });
      void qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}

/** DELETE /moderation/matches/{id} — withdraw a submission that is not approved. */
export function useCancelSubmission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (matchId: string) => api.delete<void>(`/moderation/matches/${matchId}`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.mySubmissions });
      void qc.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}
