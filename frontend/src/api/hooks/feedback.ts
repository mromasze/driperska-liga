import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import type { MatchFeedbackSummary, MyFeedback, RateableMatch } from '../types';
import { useAuthStore } from '../../store/auth';

const KEY = ['rateable-matches'] as const;

export function useRateableMatches() {
  return useQuery({ queryKey: KEY, queryFn: () => api.get<RateableMatch[]>('/matches/rateable') });
}

/** Aggregated peer feedback for a match — only fetched when signed in (endpoint requires auth). */
export function useMatchFeedbackSummary(matchId: string | undefined) {
  const token = useAuthStore((s) => s.accessToken);
  return useQuery({
    queryKey: ['match-feedback-summary', matchId],
    queryFn: () => api.get<MatchFeedbackSummary>(`/matches/${matchId}/feedback-summary`),
    enabled: Boolean(matchId) && Boolean(token),
  });
}

export function useSubmitFeedback() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ matchId, upvotePlayerId, downvotePlayerId, note }:
      { matchId: string; upvotePlayerId: string | null; downvotePlayerId: string | null; note: string | null }) =>
      api.post<MyFeedback>(`/matches/${matchId}/feedback`, { upvotePlayerId, downvotePlayerId, note }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
